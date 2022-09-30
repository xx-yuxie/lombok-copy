package com.xiaomi.lombok.copy.api.plugin;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.*;
import com.xiaomi.lombok.copy.api.Data;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Set;
import java.util.stream.Collectors;

@SupportedAnnotationTypes("com.xiaomi.lombok.copy.api.Data")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class DataProcessor extends AbstractProcessor{
    private JavacTrees trees;
    private TreeMaker treeMaker;
    private Names names;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        System.out.println("**************** init ************* " );
        super.init(processingEnv);
        this.trees = JavacTrees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment)processingEnv).getContext();
        this.treeMaker = TreeMaker.instance(context);
        this.names = Names.instance(context);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        System.out.println("************ process ************************");
        Set<? extends Element> set = roundEnv.getElementsAnnotatedWith(Data.class);
        for (Element element : set) {
            // 根据element的到tree
            JCTree jcTree = trees.getTree(element);
            // 对jcTree进行修改
            jcTree.accept(new TreeTranslator(){
                @Override
                public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
                    java.util.List<JCTree.JCVariableDecl> jcVariableDeclList = jcClassDecl.defs.stream()
                            // 过滤，只处理变量类型
                            .filter(it -> it.getKind().equals(Tree.Kind.VARIABLE))
                            // 类型强转
                            .map(it -> (JCTree.JCVariableDecl) it).collect(Collectors.toList());
                    // 获取到所有的变量之后对所有的变量进行新增set,get方法
                    for (JCTree.JCVariableDecl jcVariableDecl : jcVariableDeclList) {
                        // 添加get方法
                        jcClassDecl.defs = jcClassDecl.defs.prepend(addGetMethod(jcVariableDecl));
                        // 添加set方法
                        jcClassDecl.defs = jcClassDecl.defs.prepend(addSetMethod(jcVariableDecl));
                    }
                    super.visitClassDef(jcClassDecl);
                }
            });
        }
        return true;
    }

    private JCTree.JCMethodDecl addSetMethod(JCTree.JCVariableDecl jcVariableDecl) {
        //生成入参
        JCTree.JCVariableDecl param = treeMaker.VarDef(treeMaker.Modifiers(Flags.PARAMETER), getSetterVariableName(jcVariableDecl.name), jcVariableDecl.vartype, null);
        List<JCTree.JCVariableDecl> parameters = List.of(param);
        //生成方法体
        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();
        //Select用于创建域访问/方法访问(这里的方法访问只是取到名字，方法的调用需要用TreeMaker.Apply)语法树节点
        //var1：'.'运算符左边的表达式
        //var2：'.'运算符右边的表达式
        //Ident 定义关键字
        JCTree.JCFieldAccess thisVariable = treeMaker.Select(treeMaker.Ident(names.fromString("this")), jcVariableDecl.name);
        //Assign用于创建赋值语句语法树节点
        //var1：赋值语句左边表达式
        //var2：赋值语句右边表达式
        //Exec用于创建可执行语句语法树节点
        statements.append(treeMaker.Exec(treeMaker.Assign(thisVariable, treeMaker.Ident(getSetterVariableName(jcVariableDecl.name)))));
        //加入方法体
        JCTree.JCBlock body = treeMaker.Block(0, statements.toList());
        //无返回对象
        //生成set方法
        //public JCMethodDecl MethodDef(
        // JCModifiers mods //访问标志,
        // Name name, // 方法名
        // JCExpression restype, // 返回类型,返回类型 restype 填写 null 或者 treeMaker.TypeIdent(TypeTag.VOID) 都代表返回 void 类型
        // List<JCTypeParameter> typarams, // 泛型参数列表
        // List<JCVariableDecl> params, // 参数列表
        // List<JCExpression> thrown, // 异常声明列表
        // JCBlock body, // 方法体
        // JCExpression defaultValue // 默认方法(可能是 interface 中的哪个 default));
        return treeMaker.MethodDef(treeMaker.Modifiers(Flags.PUBLIC),
                getSetterMethodName(jcVariableDecl.getName()), treeMaker.Type(new Type.JCVoidType()),
                List.nil(), parameters, List.nil(), body,null);
    }

    private Name getGetterMethodName(Name name) {
        // 拼接get方法名称
        String s = name.toString();
        //eg: name -> getName
        return names.fromString("get" + s.substring(0,1).toUpperCase() + s.substring(1, name.length()));
    }

    private Name getSetterMethodName(Name name) {
        // 拼接set方法名称
        String s = name.toString();
        //eg: name -> setName
        return names.fromString("set" + s.substring(0,1).toUpperCase() + s.substring(1, name.length()));
    }

    private Name getSetterVariableName(Name name) {
        // 拼接入参变量名称
        String s = name.toString();
        //eg: name -> newName
        return names.fromString("new" + s.substring(0,1).toUpperCase() + s.substring(1, name.length()));
    }

    private JCTree.JCMethodDecl addGetMethod(JCTree.JCVariableDecl jcVariableDecl) {
        //无入参
        //生成方法体
        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();
        // 返回结果 return name 也算方法体的一部分
        JCTree.JCReturn jcReturn = treeMaker.Return(treeMaker.Ident(jcVariableDecl.name));
        statements.append(jcReturn);
        JCTree.JCBlock jcBlock = treeMaker.Block(0, statements.toList());
        //构造方法
        return treeMaker.MethodDef(treeMaker.Modifiers(Flags.PUBLIC),
                getGetterMethodName(jcVariableDecl.getName()), treeMaker.Type(jcVariableDecl.getType().type), List.nil(), List.nil(), List.nil(), jcBlock, null);
    }
}
