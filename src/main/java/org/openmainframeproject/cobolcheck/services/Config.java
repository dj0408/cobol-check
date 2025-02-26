/*
Copyright 2020 David Nicolette

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package org.openmainframeproject.cobolcheck.services;

import org.openmainframeproject.cobolcheck.features.launcher.Formatter.DataTransferObjects.DataTransferObjectStyle;
import org.openmainframeproject.cobolcheck.features.launcher.Formatter.Formats.TestOutputFormat;
import org.openmainframeproject.cobolcheck.services.log.Log;
import org.openmainframeproject.cobolcheck.exceptions.IOExceptionProcessingConfigFile;
import org.openmainframeproject.cobolcheck.exceptions.PossibleInternalLogicErrorException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Loads and manages configuration settings.
 *
 * @author Dave Nicolette (neopragma)
 * @since 14
 */
public class Config {

    public static final String DECIMAL_POINT_IS_COMMA_CONFIG_KEY = "cobolcheck.decimalPointIsComma";
    public static final String INJECT_START_TAG_CONFIG_KEY = "cobolcheck.injectedCodeTag.start";
    public static final String INJECT_END_TAG_CONFIG_KEY = "cobolcheck.injectedCodeTag.end";
    public static final String GENERATED_CODE_PATH = "cobolcheck.test.program.path";
    public static final String GENERATED_FILES_PERMISSION_ALL = "generated.files.permission.all";
    public static final String LOCALE_LANGUAGE_CONFIG_KEY = "locale.language";
    public static final String LOCALE_COUNTRY_CONFIG_KEY = "locale.country";
    public static final String LOCALE_VARIANT_CONFIG_KEY = "locale.variant";
    public static final String DEFAULT_LOCALE_CONFIG_KEY = "default.locale";
    public static final String RUN_GENERATED_TESTS = "cobolcheck.test.run";
    public static final String RESOLVED_APPLICATION_SOURCE_FILENAME_SUFFIX = "resolved.application.source.filename.suffix";
    public static final String APPLICATION_SOURCE_FILENAME_SUFFIX = "application.source.filename.suffix";
    public static final String RESOLVED_APPLICATION_COPYBOOK_FILENAME_SUFFIX = "resolved.application.copybook.filename.suffix";
    public static final String APPLICATION_COPYBOOK_FILENAME_SUFFIX = "application.copybook.filename.suffix";
    public static final String NONE = "none";
    public static final String DEFAULT_CONFIG_FILE_PATH = "config.properties";
    public static final String TEST_SUITE_DIRECTORY_CONFIG_KEY = "test.suite.directory";
    public static final String TEST_RESULTS_FILE_CONFIG_KEY = "test.results.file";
    public static final String TEST_RESULTS_FORMAT_CONFIG_KEY = "test.results.format";
    public static final String TEST_RESULTS_FORMAT_STYLE_CONFIG_KEY = "test.results.format.style";
    public static final String APPLICATION_SOURCE_DIRECTORY_CONFIG_KEY = "application.source.directory";
    public static final String DEFAULT_APPLICATION_SOURCE_DIRECTORY = "src/main/cobol";

    private static Properties settings = null;

    private Config(){ }

    public static void load() {
        load(DEFAULT_CONFIG_FILE_PATH);
    }

    public static void load(String configResourceName) {
        Log.info(Messages.get("INF001", configResourceName));
        try(FileInputStream configSettings = new FileInputStream(configResourceName)){
            settings = new Properties();
            settings.load(configSettings);
        } catch (IOException ioe) {
            File f = new File(configResourceName);
            throw new IOExceptionProcessingConfigFile(
                    Messages.get("ERR003", f.getAbsolutePath()), ioe);
        } catch (NullPointerException npe) {
            throw new PossibleInternalLogicErrorException(
                    Messages.get("ERR001",
                            "configResourceName", "Config.load(configResourceName)"),
                            npe);
        }
        setDecimalPointIsCommaFromFile();
        setDefaultLocaleOverride();
        Log.info(Messages.get("INF002", configResourceName));

        setApplicationFilenameSuffixes();
        setCopybookFilenameSuffix();
    }

    public static String getString(String key) {
        return getString(key, Constants.EMPTY_STRING);
    }

    public static String getString(String key, String defaultValue) {
        return (String) settings.getOrDefault(key, defaultValue);
    }

    public static void remove(String key) {
        settings.remove(key);
    }

    public static Locale getDefaultLocale() { return (Locale) settings.get(DEFAULT_LOCALE_CONFIG_KEY); }

    private static String testCodePrefix = "";
    public static String getTestCodePrefix() {
        if (testCodePrefix.isEmpty()){
            testCodePrefix = getString(Constants.COBOLCHECK_PREFIX_CONFIG_KEY, Constants.DEFAULT_COBOLCHECK_PREFIX);
            if (testCodePrefix.length() > 3)
                throw new PossibleInternalLogicErrorException("cobolcheck prefix defined in 'config.properties' must be 3 or less characters long. Given value: '"
                    + testCodePrefix + "' is too long");
            if (testCodePrefix.length() == 0)
                throw new PossibleInternalLogicErrorException("cobolcheck prefix defined in 'config.properties' cannot be empty");

        }

        return testCodePrefix;
    }

    static boolean decimalPointIsComma = false;
    public static void setDecimalPointIsCommaFromFile() {
        String value = StringHelper.adjustPathString(settings.getProperty(DECIMAL_POINT_IS_COMMA_CONFIG_KEY,
                Constants.CURRENT_DIRECTORY));
            decimalPointIsComma = Boolean.parseBoolean(value.trim());
    }

    public static  boolean isDecimalPointComma() {
        return decimalPointIsComma;
    }

    public static void setDecimalPointIsComma(boolean value) {
        decimalPointIsComma = value;
    }

    public static String getTestSuiteDirectoryPathString() {
        return StringHelper.adjustPathString(settings.getProperty(TEST_SUITE_DIRECTORY_CONFIG_KEY,
                Constants.CURRENT_DIRECTORY));
    }

    public static String getGeneratedTestCodePath() {
        return StringHelper.adjustPathString(settings.getProperty(GENERATED_CODE_PATH,
                Constants.CURRENT_DIRECTORY));
    }

    public static String getGeneratedFilesPermissionAll() {
        String permissions = settings.getProperty(GENERATED_FILES_PERMISSION_ALL, Constants.CURRENT_DIRECTORY);
        if (permissions.toUpperCase(Locale.ROOT).trim().equals("NONE"))
            return "";
        else
            return permissions;
    }

    public static String getInjectStartTag(){
        return StringHelper.adjustPathString(settings.getProperty(INJECT_START_TAG_CONFIG_KEY,
                Constants.CURRENT_DIRECTORY));
    }

    public static String getInjectEndTag(){
        return StringHelper.adjustPathString(settings.getProperty(INJECT_END_TAG_CONFIG_KEY,
                Constants.CURRENT_DIRECTORY));
    }

    public static String getTestResultFilePathString() {
        String pathFromConfig = settings.getProperty(TEST_RESULTS_FILE_CONFIG_KEY, Constants.CURRENT_DIRECTORY);
        return StringHelper.adjustPathString(StringHelper.changeFileExtension(pathFromConfig, getTestResultFormat().name()));
    }

    static String generatedTestFileName = "";
    public static String getGeneratedTestFileName() {
        if (generatedTestFileName.isEmpty()){
            generatedTestFileName = settings.getProperty(Constants.TEST_PROGRAM_NAME_CONFIG_KEY, Constants.CURRENT_DIRECTORY);
        }
        return generatedTestFileName;
    }

    public static void setGeneratedTestFileName(String keyValue) {
        generatedTestFileName = keyValue;
    }

    static String concatenatedTestSuitePath = "";
    public static String getConcatenatedTestSuitesPath(){
        if (concatenatedTestSuitePath.isEmpty()){
            concatenatedTestSuitePath = StringHelper.adjustPathString(settings.getProperty(
                    Constants.CONCATENATED_TEST_SUITES_CONFIG_KEY, Constants.CURRENT_DIRECTORY));
        }
        return concatenatedTestSuitePath;
    }

    public static void setConcatenatedTestSuitesPath(String keyValue)
    {
        concatenatedTestSuitePath = StringHelper.adjustPathString(keyValue);
    }

    public static TestOutputFormat getTestResultFormat() {
        String format = StringHelper.adjustPathString(settings.getProperty(TEST_RESULTS_FORMAT_CONFIG_KEY, Constants.CURRENT_DIRECTORY));

        switch (format.toUpperCase(Locale.ROOT)){

            case "XML":
                return TestOutputFormat.xml;
            case "TXT":
            default:
                return TestOutputFormat.txt;
        }
    }

    public static DataTransferObjectStyle getTestResultFormatStyle() {
        String style = StringHelper.adjustPathString(settings.getProperty(TEST_RESULTS_FORMAT_STYLE_CONFIG_KEY, Constants.CURRENT_DIRECTORY));

        switch (style.toUpperCase(Locale.ROOT)){

            case "JUNIT":
                return DataTransferObjectStyle.JUnit;
            case "DIRECTOUTPUT":
            default:
                return DataTransferObjectStyle.directOutput;
        }
    }

    public static boolean getRunGeneratedTest() {
        String value = StringHelper.adjustPathString(settings.getProperty(RUN_GENERATED_TESTS,
                Constants.CURRENT_DIRECTORY));
        return Boolean.parseBoolean(value.trim());
    }

    public static String getApplicationSourceDirectoryPathString() {
        return StringHelper.adjustPathString(settings.getProperty(APPLICATION_SOURCE_DIRECTORY_CONFIG_KEY,
                DEFAULT_APPLICATION_SOURCE_DIRECTORY));
    }

    public static List<String> getApplicationFilenameSuffixes() {
        return (List<String>) settings.get(RESOLVED_APPLICATION_SOURCE_FILENAME_SUFFIX);
    }

    private static void setApplicationFilenameSuffixes() {
        resolveFilenameSuffixes(APPLICATION_SOURCE_FILENAME_SUFFIX, RESOLVED_APPLICATION_SOURCE_FILENAME_SUFFIX);
    }

    public static List<String> getCopybookFilenameSuffixes() {
        return (List<String>) settings.get(RESOLVED_APPLICATION_COPYBOOK_FILENAME_SUFFIX);
    }

    private static void setCopybookFilenameSuffix() {
        resolveFilenameSuffixes(APPLICATION_COPYBOOK_FILENAME_SUFFIX, RESOLVED_APPLICATION_COPYBOOK_FILENAME_SUFFIX);
    }

    private static void resolveFilenameSuffixes(String configKey, String resolvedConfigKey) {
        String suffixSpecification = getString(configKey, NONE);
        List<String> suffixes = new ArrayList();
        String[] suffixValues = null;
        if (!suffixSpecification.equalsIgnoreCase(NONE)) {
            suffixValues = suffixSpecification.split(Constants.COMMA);
            for (String suffixValue : suffixValues) {
                suffixes.add(Constants.PERIOD + suffixValue);
            }
        }
        settings.put(resolvedConfigKey, suffixes);
    }

    private static void setDefaultLocaleOverride() {
        if (!settings.containsKey(LOCALE_LANGUAGE_CONFIG_KEY)) {
            return;
        }
        Locale locale;
        if (!settings.containsKey(LOCALE_COUNTRY_CONFIG_KEY)) {
            locale = new Locale(settings.getProperty(LOCALE_LANGUAGE_CONFIG_KEY));
        } else if (!settings.containsKey(LOCALE_VARIANT_CONFIG_KEY)) {
            locale = new Locale(
                    settings.getProperty(LOCALE_LANGUAGE_CONFIG_KEY),
                    settings.getProperty(LOCALE_COUNTRY_CONFIG_KEY));
        } else {
            locale = new Locale(settings.getProperty(LOCALE_LANGUAGE_CONFIG_KEY),
                    settings.getProperty(LOCALE_COUNTRY_CONFIG_KEY),
                    settings.getProperty(LOCALE_VARIANT_CONFIG_KEY));
        }
        settings.put(DEFAULT_LOCALE_CONFIG_KEY, locale);
    }
}
