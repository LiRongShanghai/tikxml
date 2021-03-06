/*
 * Copyright (C) 2015 Hannes Dorfmann
 * Copyright (C) 2015 Tickaroo, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.tickaroo.tikxml.processor;

import com.google.auto.service.AutoService;
import com.tickaroo.tikxml.annotation.ScanMode;
import com.tickaroo.tikxml.annotation.Xml;
import com.tickaroo.tikxml.processor.field.AnnotatedClass;
import com.tickaroo.tikxml.processor.field.AnnotatedClassImpl;
import com.tickaroo.tikxml.processor.generator.TypeAdapterCodeGenerator;
import com.tickaroo.tikxml.processor.scanning.AnnotationBasedRequiredDetector;
import com.tickaroo.tikxml.processor.scanning.FieldDetectorStrategyFactory;
import com.tickaroo.tikxml.processor.scanning.FieldScanner;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

/**
 * Annotation processor for @Xml annotated images
 *
 * @author Hannes Dorfmann
 * @since 1.0
 */
@AutoService(Processor.class)
public class XmlProcessor extends AbstractProcessor {

  /**
   * The default scan mode
   */
  private static final String OPTION_DEFAULT_SCAN_MODE = "defaultScanMode";
  private static final String OPTION_TYPE_CONVERTER_FOR_PRIMITIVES = "primitiveTypeConverters";

  private Messager messager;
  private Filer filer;
  private Elements elementUtils;
  private Types typeUtils;
  private FieldDetectorStrategyFactory fieldDetectorStrategyFactory;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    messager = processingEnv.getMessager();
    filer = processingEnv.getFiler();
    elementUtils = processingEnv.getElementUtils();
    typeUtils = processingEnv.getTypeUtils();
    fieldDetectorStrategyFactory = new FieldDetectorStrategyFactory(elementUtils, typeUtils,
        new AnnotationBasedRequiredDetector());
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    Set<String> types = new HashSet<>();
    types.add(Xml.class.getCanonicalName());
    return types;
  }

  @Override
  public Set<String> getSupportedOptions() {
    Set<String> options = new HashSet<>();
    options.add(OPTION_DEFAULT_SCAN_MODE);
    options.add(OPTION_TYPE_CONVERTER_FOR_PRIMITIVES);
    return options;
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

    try {

      // Read options
      String optionScanAsString = processingEnv.getOptions().get(OPTION_DEFAULT_SCAN_MODE);
      ScanMode defaultScanMode;
      if (optionScanAsString == null || optionScanAsString.length() == 0) {
        defaultScanMode = ScanMode.ANNOTATIONS_ONLY;
      } else {
        try {
          defaultScanMode = ScanMode.valueOf(optionScanAsString);
        } catch (Exception e) {
          throw new ProcessingException(null,
              "The option '%s' is not allowed. Must either be %s or %s",
              OPTION_DEFAULT_SCAN_MODE, optionScanAsString, ScanMode.ANNOTATIONS_ONLY.toString(),
              ScanMode.COMMON_CASE.toString());
        }
      }

      if (defaultScanMode == ScanMode.DEFAULT) {
        throw new ProcessingException(null,
            "The option '%s' is not allowed. Must either be %s or %s",
            OPTION_DEFAULT_SCAN_MODE, optionScanAsString, ScanMode.ANNOTATIONS_ONLY.toString(),
            ScanMode.COMMON_CASE.toString());
      }

      String primitiveTypeConverterOptions =
          processingEnv.getOptions().get(OPTION_TYPE_CONVERTER_FOR_PRIMITIVES);
      Set<String> primitiveTypeConverters =
          readPrimitiveTypeConverterOptions(primitiveTypeConverterOptions);

      FieldScanner scanner =
          new FieldScanner(elementUtils, typeUtils, fieldDetectorStrategyFactory);
      Set<? extends Element> elementsAnnotatedWith = roundEnv.getElementsAnnotatedWith(Xml.class);

      for (Element element : elementsAnnotatedWith) {

        // Skip abstract classes
        if (element.getKind() == ElementKind.CLASS && element.getModifiers()
            .contains(Modifier.ABSTRACT)) {
          continue;
        }

        AnnotatedClass clazz = new AnnotatedClassImpl(element);

        // Scan class
        scanner.scan(clazz);

        TypeAdapterCodeGenerator generator =
            new TypeAdapterCodeGenerator(filer, elementUtils, primitiveTypeConverters);
        generator.generateCode(clazz);
      }
    } catch (ProcessingException e) {
      printError(e);
    }

    return false;
  }

  Set<String> readPrimitiveTypeConverterOptions(String optionsAsString) {
    Set<String> primitiveTypeConverters = new HashSet<String>();

    if (optionsAsString != null && optionsAsString.length() > 0) {
      String[] options = optionsAsString.split(",");
      for (String o : options) {
        primitiveTypeConverters.add(o.trim());
      }
    }

    return primitiveTypeConverters;
  }

  /**
   * Prints the error message
   *
   * @param exception The exception that has caused an error
   */

  private void printError(ProcessingException exception) {
    messager.printMessage(Diagnostic.Kind.ERROR, exception.getMessage(), exception.getElement());
  }
}
