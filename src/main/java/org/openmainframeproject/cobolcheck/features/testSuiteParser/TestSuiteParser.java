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
package org.openmainframeproject.cobolcheck.features.testSuiteParser;

import org.openmainframeproject.cobolcheck.exceptions.PossibleInternalLogicErrorException;
import org.openmainframeproject.cobolcheck.exceptions.TestSuiteCouldNotBeReadException;
import org.openmainframeproject.cobolcheck.exceptions.VerifyReferencesNonexistentMockException;
import org.openmainframeproject.cobolcheck.features.interpreter.StringTokenizerExtractor;
import org.openmainframeproject.cobolcheck.services.cobolLogic.*;
import org.openmainframeproject.cobolcheck.services.Config;
import org.openmainframeproject.cobolcheck.services.Constants;
import org.openmainframeproject.cobolcheck.services.Messages;
import org.openmainframeproject.cobolcheck.services.cobolLogic.*;
import org.openmainframeproject.cobolcheck.services.log.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Parses the concatenated test suite and writes Cobol test code to the output stream for the generated test program.
 *
 * @author Dave Nicolette (neopragma)
 * @since 14
 */
public class TestSuiteParser {
    private final KeywordExtractor keywordExtractor;
    private List<String> testSuiteTokens;
    private String currentTestSuiteLine;

    // Source tokens used in fully-qualified data item names
    private final List<String> qualifiedNameKeywords = Arrays.asList("IN", "OF");

    private final BeforeAfterRepo beforeAfterRepo;

    //Used for mocking
    MockRepository mockRepository;
    private Mock currentMock;
    private String currentMockArgument = "";
    private int mockNumber;
    private boolean expectMockIdentifier;
    boolean expectUsing;
    boolean expectMockArguments;
    private boolean ignoreCobolStatementKeyAction;
    private VerifyMockCount currentVerify;
    private boolean verifyInProgress;


    // Optionally replace identifier prefixes in cobol-check copybook lines and generated source lines,
    // in case of conflict with prefixes used in programs to be tested.
    // This is set in config.properties, cobolcheck.prefix entry.
    private final String testCodePrefix;

    // Flags to keep track of context while reading input source files.
    // We want to make a single pass of all inputs, so we need to know what we are looking for at any given point.
    private boolean emptyTestSuite;
    private boolean cobolStatementInProgress;
    private boolean expectInProgress;
    private boolean toBeInProgress;
    private boolean boolean88LevelCompare;
    private boolean expectTestsuiteName;
    private boolean expectTestcaseName;
    private String fieldNameForExpect;
    private boolean possibleQualifiedName;
    private boolean expectQualifiedName;
    private String expectedValueToCompare;
    private boolean reverseCompare;
    private boolean greaterThanComparison;
    private boolean lessThanComparison;
    private KeywordAction nextAction = KeywordAction.NONE;
    private String currentTestSuiteName = Constants.EMPTY_STRING;
    private int testSuiteNumber = 0;
    private String currentTestCaseName = Constants.EMPTY_STRING;
    private int testCaseNumber = 0;

    // Lines inserted into the test program
    private static final String COBOL_PERFORM_INITIALIZE =
            "           PERFORM %sINITIALIZE";
    private static final String COBOL_DISPLAY_TESTSUITE =
            "           DISPLAY \"TESTSUITE:\"";
    private static final String COBOL_DISPLAY_NAME =
            "           DISPLAY %s";
    private static final String COBOL_STORE_TESTCASE_NAME_1 =
            "           MOVE %s";
    private static final String COBOL_STORE_TESTCASE_NAME_2 =
            "               TO %sTEST-CASE-NAME";
    private static final String COBOL_STORE_TESTSUITE_NAME_1 =
            "           MOVE %s";
    private static final String COBOL_STORE_TESTSUITE_NAME_2 =
            "               TO %sTEST-SUITE-NAME";
    private static final String COBOL_PERFORM_BEFORE =
            "           PERFORM %sBEFORE-EACH";
    private static final String COBOL_PERFORM_INITIALIZE_MOCK_COUNT =
            "           PERFORM %sINITIALIZE-MOCK-COUNT";
    private static final String COBOL_INCREMENT_TEST_CASE_COUNT =
            "           ADD 1 TO %sTEST-CASE-COUNT";

    /**
     * Example: This will look like:
     * SET UT-NORMAL-COMPARE TO TRUE
     * or when NOT is specified:
     * SET UT-REVERSE-COMPARE TO TRUE
     */
    private static final String COBOL_SET_NORMAL_OR_REVERSE_COMPARE =
            "           SET %1$s%2$s-COMPARE TO %3$s";

    private static final String COBOL_SET_COMPARE_NUMERIC =
            "           SET %1$sNUMERIC-COMPARE TO %2$s";
    private static final String COBOL_SET_COMPARE_88_LEVEL =
            "           SET %1$sCOMPARE-88-LEVEL TO %2$s";

    /**
     * Example: This will look like:
     * SET UT-RELATION-GT for "greater than"
     * SET UT-RELATION-LT for "less than"
     * SET UT-RELATION-EQ for "equal to"
     */
    private static final String COBOL_SET_RELATION =
            "           SET %1$sRELATION-%2$s TO %3$s";

    private static final String COBOL_MOVE_FIELDNAME_TO_ACTUAL =
            "           MOVE %2$s TO %1$sACTUAL";
    private static final String COBOL_MOVE_FIELDNAME_TO_ACTUAL_NUMERIC =
            "           MOVE %2$s TO %1$sACTUAL-NUMERIC";
    private static final String COBOL_MOVE_EXPECTED_ALPHANUMERIC_LITERAL_1 =
            "           MOVE %s";
    private static final String COBOL_MOVE_EXPECTED_ALPHANUMERIC_LITERAL_2 =
            "               TO %sEXPECTED";
    private static final String COBOL_MOVE_EXPECTED_NUMERIC_LITERAL =
            "           MOVE %2$s TO %1$sEXPECTED-NUMERIC";
    private static final String COBOL_SET_ACTUAL_88_VALUE_1 =
            "           IF %1$s";
    private static final String COBOL_SET_ACTUAL_88_VALUE_2 =
            "               SET %1$sACTUAL-88-VALUE TO TRUE";
    private static final String COBOL_SET_ACTUAL_88_VALUE_3 =
            "               MOVE 'TRUE' TO %1$sACTUAL";
    private static final String COBOL_SET_ACTUAL_88_VALUE_4 =
            "           ELSE";
    private static final String COBOL_SET_ACTUAL_88_VALUE_5 =
            "               SET %1$sACTUAL-88-VALUE TO FALSE";
    private static final String COBOL_SET_ACTUAL_88_VALUE_6 =
            "               MOVE 'FALSE' TO %1$sACTUAL";
    private static final String COBOL_SET_ACTUAL_88_VALUE_7 =
            "           END-IF";

    private static final String COBOL_SET_EXPECTED_88_VALUE_1 =
            "           IF %1$sEXPECTED-88-VALUE";
    private static final String COBOL_SET_EXPECTED_88_VALUE_2 =
            "               MOVE 'TRUE' TO %1$sEXPECTED";
    private static final String COBOL_SET_EXPECTED_88_VALUE_3 =
            "           ELSE";
    private static final String COBOL_SET_EXPECTED_88_VALUE_4 =
            "               MOVE 'FALSE' TO %1$sEXPECTED";
    private static final String COBOL_SET_EXPECTED_88_VALUE_5 =
            "           END-IF";
    private static final String COBOL_SET_EXPECTED_88_VALUE =
            "           SET %1$sEXPECTED-88-VALUE TO %2$s";
    private static final String COBOL_SET_ALPHANUMERIC_COMPARE =
            "           SET %1$sALPHANUMERIC-COMPARE TO %2$s";
    private static final String COBOL_CHECK_EXPECTATION =
            "           PERFORM %sCHECK-EXPECTATION";
    private static final String COBOL_PERFORM_AFTER =
            "           PERFORM %sAFTER-EACH";
    private static final String ELEVEN_LEADING_SPACES = "           ";

    private static final String COBOL_SET_ACTUAL_MOCK_ACCESSES =
            "           MOVE %1$s TO %2$sACTUAL-ACCESSES";
    private static final String COBOL_SET_EXPECTED_MOCK_ACCESSES =
            "           MOVE %1$s TO %2$sEXPECTED-ACCESSES";
    private static final String COBOL_SET_MOCK_OPERATION =
            "           MOVE %1$s TO %2$sMOCK-OPERATION";
    private static final String COBOL_SET_VERIFY_EXACT =
            "           SET %1$sVERIFY-EXACT TO %2$s";
    private static final String COBOL_SET_VERIFY_AT_LEAST =
            "           SET %1$sVERIFY-AT-LEAST TO %2$s";
    private static final String COBOL_SET_VERIFY_NO_MORE_THAN =
            "           SET %1$sVERIFY-NO-MORE-THAN TO %2$s";
    private static final String COBOL_PERFORM_ASSERT_ACCESS =
            "           PERFORM %1$sASSERT-ACCESSES";
    private static final String COBOL_MOVE =
            "           MOVE %1$s TO %2$s";


    private static final String RELATION_EQ = "EQ";
    private static final String RELATION_GT = "GT";
    private static final String RELATION_LT = "LT";
    private static final String NORMAL = "NORMAL";
    private static final String REVERSE = "REVERSE";

    private StringBuffer cobolStatement;
    private NumericFields numericFields;

    public TestSuiteParser(KeywordExtractor keywordExtractor, MockRepository mockRepository, BeforeAfterRepo beforeAfterRepo) {
        this.keywordExtractor = keywordExtractor;
        this.mockRepository = mockRepository;
        this.beforeAfterRepo = beforeAfterRepo;
        testSuiteTokens = new ArrayList<>();
        emptyTestSuite = true;
        testCodePrefix = Config.getString(Constants.COBOLCHECK_PREFIX_CONFIG_KEY, Constants.DEFAULT_COBOLCHECK_PREFIX);
        initializeCobolStatement();
    }

    /**
     * Process the test suite as a series of tokens. When we have processed all the input, getNextTokenFromTestSuite()
     * returns a null reference.
     *  @param testSuiteReader - reader attached to the concatenated test suite files.
     *
     */
    public List<String> getParsedTestSuiteLines(BufferedReader testSuiteReader,
                                                NumericFields numericFieldsList) {
        List<String> parsedTestSuiteLines = new ArrayList<>();
        numericFields = numericFieldsList;
        String testSuiteToken = getNextTokenFromTestSuite(testSuiteReader);
        while (testSuiteToken != null) {
            if (!testSuiteToken.startsWith(Constants.QUOTE) && !testSuiteToken.startsWith(Constants.APOSTROPHE)) {
                testSuiteToken = testSuiteToken.toUpperCase(Locale.ROOT);
            }

            Keyword keyword = Keywords.getKeywordFor(testSuiteToken);
            Log.debug("Generator.parseTestSuite(), " +
                    "testSuiteToken <" + testSuiteToken + ">, \tkeyword.value() <" + keyword.value() + ">");

            // take actions triggered by the type of the current token
            switch (keyword.value()) {
                case Constants.TESTSUITE_KEYWORD:
                    expectTestsuiteName = true;
                    break;

                case Constants.TESTCASE_KEYWORD:
                    expectTestcaseName = true;
                    break;

                case Constants.EXPECT_KEYWORD:
                    if (cobolStatementInProgress) {
                        addUserWrittenCobolStatement(parsedTestSuiteLines);
                    }
                    cobolStatementInProgress = false;
                    initializeCobolStatement();
                    addIncrementTestCaseCountLine(parsedTestSuiteLines);
                    expectInProgress = true;
                    reverseCompare = false;
                    fieldNameForExpect = Constants.EMPTY_STRING;
                    break;

                case Constants.NOT_KEYWORD:
                    reverseCompare = true;
                    break;

                case Constants.NOT_EQUAL_SIGN_KEYWORD:
                    toBeInProgress = true;
                    // this means the user wrote "NOT !="
                    reverseCompare = !reverseCompare;
                    break;

                case Constants.GREATER_THAN_SIGN_KEYWORD:
                    toBeInProgress = true;
                    greaterThanComparison = true;
                    break;

                case Constants.LESS_THAN_SIGN_KEYWORD:
                    toBeInProgress = true;
                    lessThanComparison = true;
                    break;

                case Constants.GREATER_THAN_EQUAL_TO_SIGN_KEYWORD:
                    toBeInProgress = true;
                    lessThanComparison = true;
                    if (reverseCompare) {
                        reverseCompare = false;
                    } else {
                        reverseCompare = true;
                    }
                    break;

                case Constants.LESS_THAN_EQUAL_TO_SIGN_KEYWORD:
                    toBeInProgress = true;
                    greaterThanComparison = true;
                    reverseCompare = !reverseCompare;
                    break;

                case Constants.COBOL_TOKEN:
                    if (expectQualifiedName) {
                        fieldNameForExpect += testSuiteToken;
                        expectQualifiedName = false;
                    }
                    if (possibleQualifiedName) {
                        if (qualifiedNameKeywords.contains(testSuiteToken)) {
                            fieldNameForExpect += Constants.SPACE + testSuiteToken + Constants.SPACE;
                            expectQualifiedName = true;
                            possibleQualifiedName = false;
                        }
                    }
                    if (expectInProgress) {
                        fieldNameForExpect = testSuiteToken;
                        expectInProgress = false;
                        possibleQualifiedName = true;
                    }
                    if (toBeInProgress) {
                        expectedValueToCompare = testSuiteToken;
                        addTestCodeForAssertion(parsedTestSuiteLines, numericFields);
                        toBeInProgress = false;
                    }
                    if (expectMockIdentifier){
                        expectMockIdentifier = false;
                        ignoreCobolStatementKeyAction = true;
                        if (!verifyInProgress){
                            if (currentMock.getType().equals(Constants.CALL_TOKEN)){
                                expectUsing = true;
                                expectMockArguments = true;
                            }
                            currentMock.setIdentifier(testSuiteToken);
                            if (!expectMockArguments) {
                                currentMock.addLines(getLinesUntilKeywordHit(testSuiteReader, Constants.ENDMOCK_KEYWORD, true));
                                //We know END-MOCK is reached here
                                mockRepository.addMock(currentMock);
                            }
                        }
                        else {
                            if (currentVerify.getType().equals(Constants.CALL_TOKEN)){
                                expectUsing = true;
                                expectMockArguments = true;
                            }
                            currentVerify.setIdentifier(testSuiteToken);
                        }
                        break;
                    }

                    if (expectMockArguments){
                        boolean currentLineContainsArgument = false;
                        if (!expectUsing){
                            currentLineContainsArgument = true;
                            ignoreCobolStatementKeyAction = true;
                            if (verifyInProgress)
                                currentVerify.addArgument(getCallArgument(currentMockArgument, testSuiteToken));
                            else
                                currentMock.addArgument(getCallArgument(currentMockArgument, testSuiteToken));

                            currentMockArgument = "";
                            if (testSuiteToken.endsWith(","))
                                break;
                        }
                        //Contains no arguments or all arguments has been added
                        expectMockArguments = false;
                        expectUsing = false;
                        if (!verifyInProgress){
                            ignoreCobolStatementKeyAction = true;
                            currentMock.addLines(getLinesUntilKeywordHit(testSuiteReader, Constants.ENDMOCK_KEYWORD, currentLineContainsArgument));
                            mockRepository.addMock(currentMock);
                        }
                    }

                    if (verifyInProgress){
                        if (testSuiteToken.equalsIgnoreCase(Constants.ZERO_TOKEN)){
                            currentVerify.setExpectedCount("0");
                        }
                    }

                    break;

                case Constants.ALPHANUMERIC_LITERAL_KEYWORD:
                    if (expectTestsuiteName) {
                        expectTestsuiteName = false;
                        currentTestSuiteName = testSuiteToken;
                        addTestSuiteNamelines(currentTestSuiteName, parsedTestSuiteLines);
                        initializeCobolStatement();
                    }
                    if (expectTestcaseName) {
                        expectTestcaseName = false;
                        currentTestCaseName = testSuiteToken;
                        addTestCaseNameLines(currentTestCaseName, parsedTestSuiteLines);
                        initializeCobolStatement();
                    }
                    if (expectMockIdentifier){
                        expectMockIdentifier = false;
                        if (!verifyInProgress){
                            if (currentMock.getType().equals(Constants.CALL_TOKEN)){
                                expectUsing = true;
                                expectMockArguments = true;
                            }
                            currentMock.setIdentifier(testSuiteToken);
                            if (!expectMockArguments) {
                                currentMock.addLines(getLinesUntilKeywordHit(testSuiteReader, Constants.ENDMOCK_KEYWORD, true));
                                //We know END-MOCK is reached here
                                mockRepository.addMock(currentMock);
                            }
                        }
                        else {
                            if (currentVerify.getType().equals(Constants.CALL_TOKEN)){
                                expectUsing = true;
                                expectMockArguments = true;
                            }
                            currentVerify.setIdentifier(testSuiteToken);
                        }
                        break;
                    }
                    if (toBeInProgress) {
                        if (testSuiteToken.startsWith(Constants.QUOTE) || testSuiteToken.startsWith(Constants.APOSTROPHE)) {
                            expectedValueToCompare = testSuiteToken;
                            addTestCodeForAssertion(parsedTestSuiteLines, numericFields);
                        }
                        toBeInProgress = false;
                    }
                    break;

                case Constants.NUMERIC_LITERAL_KEYWORD:
                    if (toBeInProgress) {
                        expectedValueToCompare = testSuiteToken;
                        addTestCodeForAssertion(parsedTestSuiteLines, numericFields);
                        toBeInProgress = false;
                    }
                    if (verifyInProgress){
                        currentVerify.setExpectedCount(testSuiteToken);
                    }
                    break;

                case Constants.BOOLEAN_VALUE:
                    if (toBeInProgress) {
                        boolean88LevelCompare = true;
                        expectedValueToCompare = testSuiteToken;
                        addTestCodeForAssertion(parsedTestSuiteLines, numericFields);
                        boolean88LevelCompare = false;
                        toBeInProgress = false;
                    } else {
                        if (cobolStatementInProgress) {
                            appendTokenToCobolStatement(testSuiteToken);
                            addUserWrittenCobolStatement(parsedTestSuiteLines);
                            initializeCobolStatement();
                        }
                        cobolStatementInProgress = false;
                    }
                    break;

                case Constants.BEFORE_EACH_TOKEN:
                    List<String> beforeLines = getLinesUntilKeywordHit(testSuiteReader, Constants.END_BEFORE_TOKEN, true);
                    beforeAfterRepo.addBeforeEachItem(testSuiteNumber, currentTestSuiteName, beforeLines);
                    break;

                case Constants.AFTER_EACH_TOKEN:
                    List<String> afterLines = getLinesUntilKeywordHit(testSuiteReader, Constants.END_AFTER_TOKEN, true);
                    beforeAfterRepo.addAfterEachItem(testSuiteNumber, currentTestSuiteName, afterLines);
                    break;

                case Constants.MOCK_KEYWORD:
                    if (cobolStatementInProgress) {
                        addUserWrittenCobolStatement(parsedTestSuiteLines);
                    }
                    cobolStatementInProgress = false;
                    mockNumber += 1;
                    currentMock = new Mock(currentTestSuiteName, currentTestCaseName,
                            testSuiteNumber, testCaseNumber, mockNumber);
                    if (testCaseNumber == 0){
                        currentMock.setScope(MockScope.Global);
                    }
                    else {
                        currentMock.setScope(MockScope.Local);
                    }
                    break;

                case Constants.MOCK_TYPE:
                    expectMockIdentifier = true;
                    if (!verifyInProgress){
                        currentMock.setType(testSuiteToken);
                    }
                    else {
                        currentVerify.setType(testSuiteToken);
                    }

                    break;

                case Constants.USING_TOKEN:
                    if (expectUsing && expectMockArguments)
                        expectUsing = false;
                    break;

                case Constants.BY_REFERENCE_TOKEN:
                case Constants.BY_CONTENT_TOKEN:
                case Constants.BY_VALUE_TOKEN:
                    if (expectMockArguments)
                        currentMockArgument = testSuiteToken.toUpperCase().replace("BY ", "");
                    break;

                case Constants.VERIFY_KEYWORD:
                    if (cobolStatementInProgress) {
                        addUserWrittenCobolStatement(parsedTestSuiteLines);
                    }
                    cobolStatementInProgress = false;
                    initializeCobolStatement();
                    verifyInProgress = true;
                    currentVerify = new VerifyMockCount();
                    break;

                case Constants.NEVER_HAPPENED_KEYWORD:
                    expectMockArguments = false;
                    currentVerify.expectExact("0");
                    handleEndOfVerifyStatement(parsedTestSuiteLines);
                    break;

                case Constants.HAPPENED_KEYWORD:
                    expectMockArguments = false;
                    break;

                case Constants.ONCE_KEYWORD:
                    currentVerify.setExpectedCount("1");
                    handleEndOfVerifyStatement(parsedTestSuiteLines);
                    break;

                case Constants.AT_LEAST_KEYWORD:
                    //Actual value is set at next token
                    currentVerify.expectAtLeast("N/A");
                    break;

                case Constants.NO_MORE_THAN_KEYWORD:
                    //Actual value is set at next token
                    currentVerify.expectNoMoreThan("N/A");
                    break;

                case Constants.TIME_KEYWORD: case Constants.TIMES_KEYWORD:
                    handleEndOfVerifyStatement(parsedTestSuiteLines);
                    break;

                case Constants.TO_BE_KEYWORD:
                case Constants.TO_EQUAL_KEYWORD:
                case Constants.EQUAL_SIGN_KEYWORD:
                    toBeInProgress = true;
                    break;
            }

            // take actions that were triggered by the previous token's action, pertaining to the current token
            switch (nextAction) {
                case TESTSUITE_NAME:
                    currentTestSuiteName = testSuiteToken;
                    currentTestCaseName = "";
                    testSuiteNumber += 1;
                    testCaseNumber = 0;
                    mockNumber = 0;
                    nextAction = KeywordAction.NONE;
                    break;
                case NEW_TESTCASE:
                    currentTestCaseName = testSuiteToken;
                    testCaseNumber += 1;
                    mockNumber = 0;
                    nextAction = KeywordAction.NONE;
                    break;
            }

            // take actions that are triggered by the current token's action
            switch (keyword.keywordAction()) {
                case COBOL_STATEMENT:
                    if (ignoreCobolStatementKeyAction){
                        ignoreCobolStatementKeyAction = false;
                        break;
                    }
                    if (CobolVerbs.isCobolVerb(testSuiteToken)) {
                        if (cobolStatementInProgress) {
                            addUserWrittenCobolStatement(parsedTestSuiteLines);
                            initializeCobolStatement();
                        }
                        cobolStatementInProgress = true;
                    }
                    appendTokenToCobolStatement(testSuiteToken);
                    break;
                case FIELDNAME:
                    if (cobolStatementInProgress) {
                        appendTokenToCobolStatement(testSuiteToken);
                    }
                    break;
            }
            nextAction = keyword.keywordAction();
            testSuiteToken = getNextTokenFromTestSuite(testSuiteReader);
        }
        if (cobolStatementInProgress) {
            addUserWrittenCobolStatement(parsedTestSuiteLines);
        }
        if (testCaseNumber != 0){
            addPerformAfterEachLine(parsedTestSuiteLines);
        }
        return parsedTestSuiteLines;
    }

    /**
     * This method hides file I/O from the test suite parsing logic so the parsing logic will be easier to understand.
     * We don't want to load the whole test suite into memory at once, as we don't know how large it may be.
     * Here we consume tokens one by one and invoke the file read routine whenever we exhaust the list of tokens.
     * When the file read routine returns a null reference, it means we have reached end-of-file on the test suite.
     * This method uses a keyword extractor instance to get tokens from the input record. "Tokens" in this context
     * may mean phrases that contain embedded spaces, like "TO BE", and quoted string literals with the quotes intact.
     * Comment lines are bypassed, as there is no need to insert them into the test program.
     *
     * @param testSuiteReader - reader attached to the concatenated test suite files.
     * @return - the next token from the testSuiteReader.
     */
    private String getNextTokenFromTestSuite(BufferedReader testSuiteReader) {
        while (testSuiteTokens.isEmpty()) {
            String testSuiteLine = readNextLineFromTestSuite(testSuiteReader);
            if (testSuiteLine == null) {
                return null;
            }
            if (testSuiteLine.length() > 0 && !testSuiteLine.trim().startsWith("*")) {
//            if (testSuiteLine.length() > 5 && testSuiteLine.charAt(6) != '*') {
                testSuiteTokens = keywordExtractor.extractTokensFrom(testSuiteLine);
            }
        }
        String testSuiteToken = testSuiteTokens.get(0);
        testSuiteTokens.remove(0);
        return testSuiteToken;
    }

    /**
     * This method performs the grunt work of reading records from the test suite input source.
     *
     * @param testSuiteReader - reader attached to the concatenated test suite files.
     * @return - line of source from the testSuiteReader.
     */
    private String readNextLineFromTestSuite(BufferedReader testSuiteReader) {
        try {
            currentTestSuiteLine = testSuiteReader.readLine();
            if (currentTestSuiteLine == null) {
                if (emptyTestSuite) {
                    throw new PossibleInternalLogicErrorException(Messages.get("ERR010"));
                }
                return null;
            }
            emptyTestSuite = false;
            return currentTestSuiteLine;
        } catch (IOException ioEx) {
            throw new TestSuiteCouldNotBeReadException(ioEx);
        } catch (Exception ex) {
            throw new PossibleInternalLogicErrorException(ex);
        }
    }

    /**
     * Finds the mock, that the current verify statement is referencing and attaches it.
     * Generates lines for verify statement and adds them to the given list.
     * @param parsedTestSuiteLines The parsed lines, that the generated lines are appended to
     * @throws VerifyReferencesNonexistentMockException if referenced mock, does not exist
     * @return - the next token from the testSuiteReader.
     */
    public void handleEndOfVerifyStatement (List<String> parsedTestSuiteLines){
        verifyInProgress = false;
        currentVerify.setAttachedMock(mockRepository.getMockFor(currentVerify.getIdentifier(), currentVerify.getType(),
                currentTestSuiteName, currentTestCaseName, currentVerify.getArguments()));

        if (currentVerify.getAttachedMock() == null){
            throw new VerifyReferencesNonexistentMockException("Cannot verify nonexistent mock for: " +
                    currentVerify.getType() + " " + currentVerify.getIdentifier() + " in the scope of testsuite: " +
                    currentTestSuiteName + ", testcase: " + currentTestCaseName);
        }
        addLinesForCurrentVerifyStatement(parsedTestSuiteLines);
    }

    /**
     * Gets an argument for a call based on the given reference type and token. If the
     * reference type is empty, the default ref-type (REFERENCE) will be assigned. All commas
     * is removed in the returned argument.
     * @param referenceType The ref-type of the argument. Can be REFERENCE, CONTENT or VALUE
     * @param value The value/variable given as argument
     * @return - The argument in the format '[ref-type] [value]'
     */
    private String getCallArgument(String referenceType, String value) {
        String outPut = "";
        if (referenceType.isEmpty())
            outPut = ("REFERENCE " + value.replace(",", ""));
        else
            outPut = (referenceType + " " + value.replace(",", ""));
        return outPut;
    }

    // Helper methods to insert code into the test program being generated based on interpretation of user-written
    // test case code.

    public String getTestInitializationLine() {
        String line = String.format(COBOL_PERFORM_INITIALIZE, testCodePrefix);
        return line;
    }

    public void addTestSuiteNamelines(String testSuiteName, List<String> parsedTestSuiteLines) {
        if (testSuiteNumber != 0)
            addPerformAfterEachLine(parsedTestSuiteLines);

        parsedTestSuiteLines.add("      *============= " + testSuiteName + " =============*");

        parsedTestSuiteLines.add(COBOL_DISPLAY_TESTSUITE);
        parsedTestSuiteLines.add(String.format(COBOL_DISPLAY_NAME, testSuiteName));
        parsedTestSuiteLines.add(String.format(COBOL_STORE_TESTSUITE_NAME_1, testSuiteName));
        parsedTestSuiteLines.add(String.format(COBOL_STORE_TESTSUITE_NAME_2, testCodePrefix));
    }

    public void addTestCaseNameLines(String testCaseName, List<String> parsedTestSuiteLines) {
        if (testCaseNumber != 0)
            addPerformAfterEachLine(parsedTestSuiteLines);

        parsedTestSuiteLines.add("      *-------- " + testCaseName);
        addPerformBeforeEachLine(parsedTestSuiteLines);

        parsedTestSuiteLines.add(String.format(COBOL_STORE_TESTCASE_NAME_1, testCaseName));
        parsedTestSuiteLines.add(String.format(COBOL_STORE_TESTCASE_NAME_2, testCodePrefix));
        parsedTestSuiteLines.add(String.format(COBOL_PERFORM_INITIALIZE_MOCK_COUNT, testCodePrefix));
    }

    public void addPerformBeforeEachLine(List<String> parsedTestSuiteLines) {
        parsedTestSuiteLines.add(String.format(COBOL_STORE_TESTCASE_NAME_1, "SPACES"));
        parsedTestSuiteLines.add(String.format(COBOL_STORE_TESTCASE_NAME_2, testCodePrefix));
        parsedTestSuiteLines.add(String.format(COBOL_PERFORM_BEFORE, testCodePrefix));
    }

    public void addPerformAfterEachLine(List<String> parsedTestSuiteLines) {
        parsedTestSuiteLines.add(String.format(COBOL_STORE_TESTCASE_NAME_1, "SPACES"));
        parsedTestSuiteLines.add(String.format(COBOL_STORE_TESTCASE_NAME_2, testCodePrefix));
        parsedTestSuiteLines.add(String.format(COBOL_PERFORM_AFTER, testCodePrefix));
    }

    void addIncrementTestCaseCountLine(List<String> parsedTestSuiteLines) {
        parsedTestSuiteLines.add(String.format(COBOL_INCREMENT_TEST_CASE_COUNT, testCodePrefix));
    }

    void addTestCodeForAssertion(List<String> parsedTestSuiteLines, NumericFields numericFields) {
        if (boolean88LevelCompare) {
            addTestCodeFor88LevelEqualityCheck(parsedTestSuiteLines);
        } else {
            addSetNormalOrReverseCompare(parsedTestSuiteLines);
            if (fieldIsANumericDataType(fieldNameForExpect)) {
                parsedTestSuiteLines.add(String.format(
                        COBOL_SET_COMPARE_NUMERIC, testCodePrefix, Constants.TRUE));
                parsedTestSuiteLines.add(String.format(
                        COBOL_MOVE_FIELDNAME_TO_ACTUAL_NUMERIC, testCodePrefix, fieldNameForExpect));
                parsedTestSuiteLines.add(String.format(
                        COBOL_MOVE_EXPECTED_NUMERIC_LITERAL, testCodePrefix, expectedValueToCompare));
            } else {
                parsedTestSuiteLines.add(String.format(
                        COBOL_SET_ALPHANUMERIC_COMPARE, testCodePrefix, Constants.TRUE));
                parsedTestSuiteLines.add(String.format(
                        COBOL_MOVE_FIELDNAME_TO_ACTUAL, testCodePrefix, fieldNameForExpect));
                parsedTestSuiteLines.add(String.format(
                        COBOL_MOVE_EXPECTED_ALPHANUMERIC_LITERAL_1, expectedValueToCompare));
                parsedTestSuiteLines.add(String.format(
                        COBOL_MOVE_EXPECTED_ALPHANUMERIC_LITERAL_2, testCodePrefix));
            }
            parsedTestSuiteLines.add(String.format(
                    COBOL_SET_RELATION,
                    testCodePrefix,
                    greaterThanComparison ? RELATION_GT
                            : lessThanComparison ? RELATION_LT
                            : RELATION_EQ,
                    Constants.TRUE));
            addFinalLines(parsedTestSuiteLines);
            greaterThanComparison = false;
            lessThanComparison = false;
        }
    }

    void addTestCodeFor88LevelEqualityCheck(List<String> parsedTestSuiteLines) {
        parsedTestSuiteLines.add(String.format(COBOL_SET_COMPARE_88_LEVEL, testCodePrefix, Constants.TRUE));
        parsedTestSuiteLines.add(String.format(COBOL_SET_ACTUAL_88_VALUE_1, fieldNameForExpect));
        parsedTestSuiteLines.add(String.format(COBOL_SET_ACTUAL_88_VALUE_2, testCodePrefix));
        parsedTestSuiteLines.add(String.format(COBOL_SET_ACTUAL_88_VALUE_3, testCodePrefix));
        parsedTestSuiteLines.add(COBOL_SET_ACTUAL_88_VALUE_4);
        parsedTestSuiteLines.add(String.format(COBOL_SET_ACTUAL_88_VALUE_5, testCodePrefix));
        parsedTestSuiteLines.add(String.format(COBOL_SET_ACTUAL_88_VALUE_6, testCodePrefix));
        parsedTestSuiteLines.add(COBOL_SET_ACTUAL_88_VALUE_7);
        if (reverseCompare) {
            if (expectedValueToCompare.equals(Constants.TRUE)) {
                expectedValueToCompare = Constants.FALSE;
            } else {
                expectedValueToCompare = Constants.TRUE;
            }
        }
        parsedTestSuiteLines.add(String.format(COBOL_SET_EXPECTED_88_VALUE, testCodePrefix, expectedValueToCompare));
        parsedTestSuiteLines.add(String.format(COBOL_SET_EXPECTED_88_VALUE_1, testCodePrefix));
        parsedTestSuiteLines.add(String.format(COBOL_SET_EXPECTED_88_VALUE_2, testCodePrefix));
        parsedTestSuiteLines.add(COBOL_SET_EXPECTED_88_VALUE_3);
        parsedTestSuiteLines.add(String.format(COBOL_SET_EXPECTED_88_VALUE_4, testCodePrefix));
        parsedTestSuiteLines.add(COBOL_SET_EXPECTED_88_VALUE_5);
        addFinalLines(parsedTestSuiteLines);
    }

    /**
     * Writes a Cobol source statement of the form SET XX-NORMAL-COMPARE TO TRUE or SET XX-REVERSE-COMPARE TO TRUE
     * depending on whether NOT was specified in an EXPECT specification.
     *
     * @param parsedTestSuiteLines - The lines that are parsed from the testsuites
     */
    void addSetNormalOrReverseCompare(List<String> parsedTestSuiteLines) {
        parsedTestSuiteLines.add(String.format(
                COBOL_SET_NORMAL_OR_REVERSE_COMPARE,
                testCodePrefix,
                reverseCompare ? REVERSE : NORMAL,
                Constants.TRUE));
        reverseCompare = false;
    }

    void addLinesForCurrentVerifyStatement(List<String> parsedTestSuiteLines) {
            parsedTestSuiteLines.add(String.format(COBOL_MOVE, currentVerify.getExpectedCount(),
                    currentVerify.getAttachedMock().getGeneratedMockCountExpectedIdentifier()));

            parsedTestSuiteLines.add(String.format(COBOL_SET_ACTUAL_MOCK_ACCESSES,
                    currentVerify.getAttachedMock().getGeneratedMockCountIdentifier(), testCodePrefix));
            parsedTestSuiteLines.add(String.format(COBOL_SET_EXPECTED_MOCK_ACCESSES,
                    currentVerify.getAttachedMock().getGeneratedMockCountExpectedIdentifier(), testCodePrefix));
            parsedTestSuiteLines.add(String.format(COBOL_SET_MOCK_OPERATION,
                    currentVerify.getAttachedMock().getGeneratedMockStringIdentifierName(), testCodePrefix));

            //Set comparison
            if (currentVerify.isSetToAtLeast()){
                parsedTestSuiteLines.add(String.format(COBOL_SET_VERIFY_AT_LEAST, testCodePrefix, "TRUE"));
            }
            else{
                if (currentVerify.isSetToNoMoreThan()){
                    parsedTestSuiteLines.add(String.format(COBOL_SET_VERIFY_NO_MORE_THAN, testCodePrefix, "TRUE"));
                }
                else {
                    parsedTestSuiteLines.add(String.format(COBOL_SET_VERIFY_EXACT, testCodePrefix, "TRUE"));
                }

            }
            parsedTestSuiteLines.add(String.format(COBOL_INCREMENT_TEST_CASE_COUNT, testCodePrefix));
            parsedTestSuiteLines.add(String.format(COBOL_PERFORM_ASSERT_ACCESS, testCodePrefix));
    }


    /**
     * Writes the final lines of Cobol for a test case, common to different kinds of test cases.
     *
     * @param parsedTestSuiteLines - The lines that are parsed from the testsuites
     */
    void addFinalLines(List<String> parsedTestSuiteLines) {
        parsedTestSuiteLines.add(String.format(COBOL_CHECK_EXPECTATION, testCodePrefix));
    }

    /**
     * Generator compiles a list of Data Division item names from the program under test that represent numeric
     * data types. This method returns true when the item name of the "actual" field in an EXPECT specification
     * is in that list.
     *
     * @param fieldNameForExpect - the field name of the "actual" reference in an EXPECT specification
     * @return true when the field name represents any numeric data type
     */
    boolean fieldIsANumericDataType(String fieldNameForExpect) {
        return numericFields.dataTypeOf(fieldNameForExpect) == DataType.PACKED_DECIMAL
                || (numericFields.dataTypeOf(fieldNameForExpect) == DataType.FLOATING_POINT)
                || (numericFields.dataTypeOf(fieldNameForExpect) == DataType.DISPLAY_NUMERIC);
    }

    /**
     * Build a Cobol statement out of tokens from the test suite input.
     * Users may code standard Cobol statements to set up preconditions for a test case.
     * These tokens may occur immediately following the test case name string.
     * Users may code standard Cobol statements to define the behavior of a MOCK.
     *
     * @param testSuiteToken - token extracted from test suit input
     */
    void appendTokenToCobolStatement(String testSuiteToken) {
        if (cobolStatement.length() > 0) cobolStatement.append(Constants.SPACE);
        cobolStatement.append(testSuiteToken);
    }

    /**
     * Insert user-written Cobol statement from a test suite (not from the program under test) into the test program
     * being generated.
     *
     * @param parsedTestSuiteLines - The lines that are parsed from the testsuites
     */
    void addUserWrittenCobolStatement(List<String> parsedTestSuiteLines) {
        parsedTestSuiteLines.add(cobolStatement.toString());
    }

    private List<String> getArgumentsForCurrentMock(BufferedReader testSuiteReader) {
        List<String> usingKeywords = Arrays.asList(
                Constants.BY_REFERENCE_TOKEN,
                Constants.BY_CONTENT_TOKEN,
                Constants.BY_VALUE_TOKEN);
        String usingLine = "";
        String line;
        boolean hasEncounteredUsingKeyword = false;
        while ((line = readNextLineFromTestSuite(testSuiteReader)) != null){
            if (line.toUpperCase().contains(Constants.USING_TOKEN)){
                if (hasEncounteredUsingKeyword)
                    break;
                hasEncounteredUsingKeyword = true;
            }
            if (!usingKeywords.contains(line.toUpperCase().trim()) && !usingLine.endsWith(",")){
                break;
            }
            usingLine += (" " + line);
        }
        return Interpreter.getUsingArgs(new CobolLine(usingLine, new StringTokenizerExtractor()));
    }

    private List<String> getLinesUntilKeywordHit(BufferedReader testSuiteReader, String endingKeyword, boolean skipCurrentLine){
        List<String> lines = new ArrayList<>();
        if(currentTestSuiteLine.toUpperCase(Locale.ROOT).contains(endingKeyword.toUpperCase(Locale.ROOT)))
            return lines;
        if (!skipCurrentLine)
            lines.add(currentTestSuiteLine);
        String line;
        while ((line = readNextLineFromTestSuite(testSuiteReader)) != null &&
                !line.toUpperCase(Locale.ROOT).contains(endingKeyword.toUpperCase(Locale.ROOT))){
            lines.add(line);
        }
        testSuiteTokens.clear();
        return lines;
    }

    private void initializeCobolStatement() {
        cobolStatement = new StringBuffer(ELEVEN_LEADING_SPACES);
    }

    public String getCurrentTestSuiteName() {
        return currentTestSuiteName;
    }

    public String getCurrentTestCaseName() {
        return currentTestCaseName;
    }

    public String getCobolStatement() {
        return cobolStatement.toString();
    }
}
