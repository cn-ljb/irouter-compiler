package com.ljb.mumway.router.apt;

import com.google.auto.service.AutoService;
import com.ljb.mumway.router.IRouter;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

@AutoService(Processor.class)
@SupportedAnnotationTypes(Constants.IROUTER_ANNOTATION)
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedOptions(Constants.MODULE_NAME)
public class IRouterProcessor extends AbstractProcessor {

    private Elements elementUtils;
    private Types typeUtils;
    private Messager messager;
    private Filer filer;

    private String moduleName;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        elementUtils = processingEnv.getElementUtils();
        typeUtils = processingEnv.getTypeUtils();
        messager = processingEnv.getMessager();
        filer = processingEnv.getFiler();

        Map<String, String> options = processingEnv.getOptions();
        if (options != null && options.size() > 0) {
            moduleName = options.get(Constants.MODULE_NAME);
            messager.printMessage(Diagnostic.Kind.NOTE, " IRouter:: Compiler ModuleName >>> " + moduleName);
        }

        if (moduleName == null || moduleName.length() == 0) {
            throw new RuntimeException("IRouter ?????????????????????<IROUTER_MODULE_NAME>??????????????????????????????build.gradle????????????");
        }

    }


    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        if (set != null && set.size() > 0) {
            // ????????????@IRouter????????????
            Set<? extends Element> elements = roundEnvironment.getElementsAnnotatedWith(IRouter.class);

            if (elements != null && elements.size() > 0) {
                parseElements(elements);
            }
        }
        return true;
    }

    private void parseElements(Set<? extends Element> elements) {

        //??????ElementUtils??????IRouterCall????????????
        TypeElement callType = elementUtils.getTypeElement(Constants.IRouterCall);
        TypeMirror callMirror = callType.asType();

        //??????ElementUtils??????IRouterCallLoader????????????
        TypeElement loaderType = elementUtils.getTypeElement(Constants.IRouterCallLoader);
        TypeMirror loaderMirror = loaderType.asType();

        for (Element element : elements) {

            TypeMirror elementMirror = element.asType();

            IRouter iRouter = element.getAnnotation(IRouter.class);
            String path = iRouter.path();

            messager.printMessage(Diagnostic.Kind.NOTE, "IRouter  path >>>>???" + path);

            if (checkPathError(path)) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@IRouter??????path??????????????????" + path);
                return;
            }

            //@IRouter?????????????????????IRouterCall????????????
            if (typeUtils.isSubtype(elementMirror, callMirror)) {
                try {
                    createLoaderFile(path, (TypeElement) element, loaderMirror, callMirror);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void createLoaderFile(String path, TypeElement element, TypeMirror loaderMirror, TypeMirror callMirror) throws Exception {
        //???????????????
        ParameterizedTypeName methodReturn = ParameterizedTypeName.get(
                ClassName.get(Map.class),
                ClassName.get(String.class),
                ClassName.get(callMirror)
        );

        // ????????????
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(Constants.METHOD_NAME_LOAD_INFO)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(methodReturn);

        //?????????
        // HashMap<String, IRouterCall> map = new HashMap<>();
        methodBuilder.addStatement("$T<$T , $T> $N = new $T<>()",
                ClassName.get(Map.class),
                ClassName.get(String.class),
                ClassName.get(callMirror),
                Constants.VAR_NAME_MAP,
                ClassName.get(HashMap.class));

        // map.put("/reservicepop_page", new ReServicePopRouter());
        methodBuilder.addStatement("$N.put($S , new $T())",
                Constants.VAR_NAME_MAP,
                path,
                ClassName.get(element));

        //  return map;
        methodBuilder.addStatement("return $N",
                Constants.VAR_NAME_MAP);

        //???????????? IRouter$$Call$$reservicepop_page
        String fileName = Constants.SDK_NAME + Constants.SEPARATOR + Constants.SUFFIX_CALL + Constants.SEPARATOR + path.replace("/", "");
        messager.printMessage(Diagnostic.Kind.NOTE, "IRouter:: Compiler ???????????? ???" + fileName);
        JavaFile.builder(
                Constants.CALL_PACKAGE,         // ??????
                TypeSpec.classBuilder(fileName) //?????????
                        .addSuperinterface(ClassName.get(loaderMirror)) //????????????
                        .addModifiers(Modifier.PUBLIC)  // public
                        .addMethod(methodBuilder.build())   //??????
                        .build()
        ).build().writeTo(filer);


    }

    private boolean checkPathError(String path) {
        return path == null
                || path.length() == 0
                || !path.startsWith("/");
    }
}
