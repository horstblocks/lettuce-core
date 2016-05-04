package com.lambdaworks.apigenerator;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.Type;

/**
 * Create sync API based on the templates.
 * 
 * @author Mark Paluch
 */
@RunWith(Parameterized.class)
public class CreateSyncApi {

    private CompilationUnitFactory factory;

    @Parameterized.Parameters(name = "Create {0}")
    public static List<Object[]> arguments() {
        List<Object[]> result = new ArrayList<>();

        for (String templateName : Constants.TEMPLATE_NAMES) {
            result.add(new Object[] { templateName });
        }

        return result;
    }

    /**
     *
     * @param templateName
     */
    public CreateSyncApi(String templateName) {

        String targetName = templateName;
        File templateFile = new File(Constants.TEMPLATES, "com/lambdaworks/redis/api/" + templateName + ".java");
        String targetPackage;

        if (templateName.contains("RedisSentinel")) {
            targetPackage = "com.lambdaworks.redis.sentinel.api.sync";
        } else {
            targetPackage = "com.lambdaworks.redis.api.sync";
        }

        factory = new CompilationUnitFactory(templateFile, Constants.SOURCES, targetPackage, targetName, commentMutator(),
                methodTypeMutator(), methodDeclaration -> true, importSupplier(), null, null);
    }

    /**
     * Mutate type comment.
     * 
     * @return
     */
    protected Function<String, String> commentMutator() {
        return s -> s.replaceAll("\\$\\{intent\\}", "Synchronous executed commands") + "* @generated by "
                + getClass().getName() + "\r\n ";
    }

    /**
     * Mutate type to async result.
     * 
     * @return
     */
    protected Function<MethodDeclaration, Type> methodTypeMutator() {
        return methodDeclaration -> methodDeclaration.getType();
    }

    /**
     * Supply addititional imports.
     * 
     * @return
     */
    protected Supplier<List<String>> importSupplier() {
        return () -> Collections.emptyList();
    }

    @Test
    public void createInterface() throws Exception {
        factory.createInterface();
    }
}
