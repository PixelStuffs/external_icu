/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.currysrc.aosp;

import static com.google.currysrc.api.process.Rules.createMandatoryRule;
import static com.google.currysrc.api.process.Rules.createOptionalRule;

import com.google.common.collect.ImmutableList;
import com.google.currysrc.Main;
import com.google.currysrc.api.RuleSet;
import com.google.currysrc.api.input.DirectoryInputFileGenerator;
import com.google.currysrc.api.input.InputFileGenerator;
import com.google.currysrc.api.output.BasicOutputSourceFileGenerator;
import com.google.currysrc.api.output.OutputSourceFileGenerator;
import com.google.currysrc.api.process.Rule;
import com.google.currysrc.api.process.ast.BodyDeclarationLocators;
import com.google.currysrc.api.process.ast.TypeLocator;
import com.google.currysrc.processors.AddMarkerAnnotation;
import com.google.currysrc.processors.HidePublicClasses;
import com.google.currysrc.processors.InsertHeader;
import com.google.currysrc.processors.ModifyQualifiedNames;
import com.google.currysrc.processors.ModifyStringLiterals;
import com.google.currysrc.processors.RenamePackage;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import joptsimple.BuiltinHelpFormatter;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.ValueConversionException;
import joptsimple.ValueConverter;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

/**
 * General transformation tool for use by various external repositories that require repackaging.
 */
public class RepackagingTransform {

  private static final PathConverter PATH_CONVERTER = new PathConverter();
  private static final PackageTransformationConverter PACKAGE_TRANSFORMATION_CONVERTER =
      new PackageTransformationConverter();

  /**
   * Usage: java ConscryptTransform {source dir} {target dir}
   */
  public static void main(String[] args) throws Exception {

    OptionParser optionParser = new OptionParser();

    OptionSpec<Path> sourceDirOption = optionParser
        .accepts("source-dir", "directory containing the source that will be transformed")
        .withRequiredArg()
        .describedAs("source directory")
        .required()
        .withValuesConvertedBy(PATH_CONVERTER);

    OptionSpec<Path> targetDirOption = optionParser
        .accepts("target-dir", "directory into which the transformed source will be written")
        .withRequiredArg()
        .describedAs("target directory")
        .required()
        .withValuesConvertedBy(PATH_CONVERTER);

    OptionSpec<PackageTransformation> packageTransformationOption = optionParser
        .accepts("package-transformation", "transformation to apply to the package")
        .withRequiredArg().withValuesConvertedBy(PACKAGE_TRANSFORMATION_CONVERTER);

    OptionSpec<Path> corePlatformApiFileOption =
        optionParser.accepts("core-platform-api-file",
            "flat file containing body declaration identifiers for those classes and members that"
                + " form part of the core platform api and so require a CorePlatformApi annotation"
                + " to be added during transformation.")
            .withRequiredArg()
            .withValuesConvertedBy(PATH_CONVERTER);

    OptionSpec<Path> intraCoreApiFileOption =
        optionParser.accepts("intra-core-api-file",
            "flat file containing body declaration identifiers for those classes and members that"
                + " form part of the intra core api and so require an IntraCoreApi annotation to be"
                + " added during transformation.")
            .withRequiredArg()
            .withValuesConvertedBy(PATH_CONVERTER);

    OptionSpec<Path> unsupportedAppUsageFileOption =
        optionParser.accepts("unsupported-app-usage-file",
            "json file containing body declaration identifiers and annotation properties for those"
                + " members that form part of the hiddenapi and so require an UnsupportedAppUsage"
                + " annotation to be added during transformation.")
            .withRequiredArg()
            .withValuesConvertedBy(PATH_CONVERTER);

    optionParser.formatHelpWith(new BuiltinHelpFormatter(120, 2));

    OptionSet optionSet;
    try {
      optionSet = optionParser.parse(args);
      if (!optionSet.nonOptionArguments().isEmpty()) {
        throw new IllegalStateException(String.format("unexpected trailing arguments %s",
            optionSet.nonOptionArguments().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "))));
      }
    } catch (RuntimeException e) {
      optionParser.printHelpOn(System.err);
      throw e;
    }

    Path sourceDir = optionSet.valueOf(sourceDirOption);
    Path targetDir = optionSet.valueOf(targetDirOption);

    ImmutableList.Builder<Rule> ruleBuilder = ImmutableList.builder();

    // Doc change: Insert a warning about the source code being generated.
    ruleBuilder.add(
        createMandatoryRule(
            new InsertHeader("/* GENERATED SOURCE. DO NOT MODIFY. */\n")));

    PackageTransformation packageTransformation = optionSet.valueOf(packageTransformationOption);
    if (packageTransformation != null) {
      String originalPackage = packageTransformation.prefixToRemove;
      String androidPackage = packageTransformation.prefixToAdd;
      ruleBuilder.add(
          // AST change: Change the package of each CompilationUnit
          createMandatoryRule(new RenamePackage(originalPackage, androidPackage)),
          // AST change: Change all qualified names in code and javadoc.
          createOptionalRule(new ModifyQualifiedNames(originalPackage, androidPackage)),
          // AST change: Change all string literals containing package names in code.
          createOptionalRule(new ModifyStringLiterals(originalPackage, androidPackage)),
          // AST change: Change all string literals containing package paths in code.
          createOptionalRule(new ModifyStringLiterals(
              packageToPath(originalPackage), packageToPath(androidPackage)))
      );
    }

    // Doc change: Insert @hide on all public classes.
    ruleBuilder.add(createHidePublicClassesRule());

    Path corePlatformApiFile = optionSet.valueOf(corePlatformApiFileOption);
    if (corePlatformApiFile != null) {
      // AST change: Add CorePlatformApi to specified classes and members
      ruleBuilder.add(
          createOptionalRule(new AddMarkerAnnotation("libcore.api.CorePlatformApi",
              BodyDeclarationLocators.readBodyDeclarationLocators(corePlatformApiFile))));
    }

    Path intraCoreApiFile = optionSet.valueOf(intraCoreApiFileOption);
    if (intraCoreApiFile != null) {
      // AST change: Add IntraCoreApi to specified classes and members
      ruleBuilder.add(
          createOptionalRule(new AddMarkerAnnotation("libcore.api.IntraCoreApi",
              BodyDeclarationLocators.readBodyDeclarationLocators(intraCoreApiFile))));
    }

    Path unsupportedAppUsageFile = optionSet.valueOf(unsupportedAppUsageFileOption);
    if (unsupportedAppUsageFile != null) {
      // AST Change: Add UnsupportedAppUsage to specified class members.
      ruleBuilder.add(
          createOptionalRule(
              Annotations.addUnsupportedAppUsage(unsupportedAppUsageFile)));
    }
    
    Map<String, String> options = JavaCore.getOptions();
    options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_8);
    options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_8);
    options.put(JavaCore.COMPILER_DOC_COMMENT_SUPPORT, JavaCore.ENABLED);
    options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);
    options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");

    new Main(false /* debug */)
        .setJdtOptions(options)
        .execute(new TransformRules(sourceDir, targetDir, ruleBuilder.build()));
  }

  private static String packageToPath(String originalPackage) {
    return "/" + originalPackage.replace('.', '/');
  }

  private static Rule createHidePublicClassesRule() {
    List<TypeLocator> publicApiClassesWhitelist = Collections.emptyList();
    return createOptionalRule(new HidePublicClasses(publicApiClassesWhitelist,
        "This class is not part of the Android public SDK API"));
  }

  static class TransformRules implements RuleSet {

    private final Path sourceDir;
    private final Path targetDir;
    private final List<Rule> rules;

    TransformRules(Path sourceDir, Path targetDir, List<Rule> rules) {
      this.sourceDir = sourceDir;
      this.targetDir = targetDir;
      this.rules = rules;
    }

    @Override
    public InputFileGenerator getInputFileGenerator() {
      return new DirectoryInputFileGenerator(sourceDir.toFile());
    }

    @Override
    public List<Rule> getRuleList(File ignored) {
      return rules;
    }

    @Override
    public OutputSourceFileGenerator getOutputSourceFileGenerator() {
      File outputDir = targetDir.toFile();
      return new BasicOutputSourceFileGenerator(outputDir);
    }
  }

  private RepackagingTransform() {
  }

  private static class PackageTransformation {

    final String prefixToRemove;
    final String prefixToAdd;

    PackageTransformation(String prefixToRemove, String prefixToAdd) {
      this.prefixToRemove = prefixToRemove;
      this.prefixToAdd = prefixToAdd;
    }

    @Override
    public String toString() {
      return String.format("%s:%s", prefixToRemove, prefixToAdd);
    }
  }

  private static class PackageTransformationConverter implements
      ValueConverter<PackageTransformation> {

    @Override
    public PackageTransformation convert(String value) {
      String[] strings = value.split(":");
      if (strings.length != 2) {
        throw new ValueConversionException(
            String.format("Expected '<prefix-to-remove>:<prefix-to-replace>' but got'%s'", value));
      }
      return new PackageTransformation(strings[0], strings[1]);
    }

    @Override
    public Class<? extends PackageTransformation> valueType() {
      return PackageTransformation.class;
    }

    @Override
    public String valuePattern() {
      return "prefix-to-remove:prefix-to-replace";
    }
  }

  private static class PathConverter implements ValueConverter<Path> {

    @Override
    public Path convert(String value) {
      return Paths.get(value);
    }

    @Override
    public Class<? extends Path> valueType() {
      return Path.class;
    }

    @Override
    public String valuePattern() {
      return "";
    }
  }
}