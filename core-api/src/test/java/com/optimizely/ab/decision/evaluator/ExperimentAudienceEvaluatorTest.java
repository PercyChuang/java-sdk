/****************************************************************************
 * Copyright 2019, Optimizely, Inc. and contributors                        *
 *                                                                          *
 * Licensed under the Apache License, Version 2.0 (the "License");          *
 * you may not use this file except in compliance with the License.         *
 * You may obtain a copy of the License at                                  *
 *                                                                          *
 *    http://www.apache.org/licenses/LICENSE-2.0                            *
 *                                                                          *
 * Unless required by applicable law or agreed to in writing, software      *
 * distributed under the License is distributed on an "AS IS" BASIS,        *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. *
 * See the License for the specific language governing permissions and      *
 * limitations under the License.                                           *
 ***************************************************************************/
package com.optimizely.ab.decision.evaluator;

import ch.qos.logback.classic.Level;
import com.optimizely.ab.config.Experiment;
import com.optimizely.ab.config.ProjectConfig;
import com.optimizely.ab.config.audience.Audience;
import com.optimizely.ab.config.audience.Condition;
import com.optimizely.ab.config.audience.TypedAudience;
import com.optimizely.ab.decision.audience.AudienceEvaluator;
import com.optimizely.ab.decision.audience.FullStackAudienceEvaluator;
import com.optimizely.ab.event.internal.UserContext;
import com.optimizely.ab.internal.LogbackVerifier;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static com.optimizely.ab.config.DatafileProjectConfigTestUtils.*;
import static com.optimizely.ab.config.ValidProjectConfigV4.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test Audience Evaluator
 */
public class ExperimentAudienceEvaluatorTest {

    @Rule
    public LogbackVerifier logbackVerifier = new LogbackVerifier();

    private static ProjectConfig projectConfig;
    private static ProjectConfig v4ProjectConfig;
    private static ProjectConfig noAudienceProjectConfig;
    private static AudienceEvaluator audienceEvaluator;

    @BeforeClass
    public static void setUp() throws IOException {
        projectConfig = validProjectConfigV2();
        noAudienceProjectConfig = noAudienceProjectConfigV2();
        audienceEvaluator = new FullStackAudienceEvaluator();
        v4ProjectConfig = validProjectConfigV4();
    }

    /**
     * If the {@link Experiment} does not have any {@link Audience}s,
     * then {@link AudienceEvaluator#evaluate(Experiment, UserContext)} should return true;
     */
    @Test
    public void evaluatesTrueIfExperimentHasNoAudiences() {
        Experiment experiment = noAudienceProjectConfig.getExperiments().get(0);
        UserContext userContext = new UserContext.Builder()
            .withProjectConfig(noAudienceProjectConfig)
            .withAttributes(Collections.<String, String>emptyMap())
            .build();
        assertTrue(audienceEvaluator.evaluate(experiment, userContext));
    }

    /**
     * If the {@link Experiment} contains at least one {@link Audience}, but attributes is empty,
     * then {@link AudienceEvaluator#evaluate(Experiment, UserContext)} should return false.
     */
    @Test
    public void evaluatesTrueIfExperimentHasAudiencesButUserHasNoAttributes() {
        Experiment experiment = projectConfig.getExperiments().get(0);
        UserContext userContext = new UserContext.Builder()
            .withProjectConfig(projectConfig)
            .withAttributes(Collections.<String, String>emptyMap())
            .build();
        assertTrue(audienceEvaluator.evaluate(experiment, userContext));
        logbackVerifier.expectMessage(Level.DEBUG,
            "Evaluating audiences for experiment \"etag1\": \"[100]\"");
        logbackVerifier.expectMessage(Level.DEBUG,
            "Starting to evaluate audience not_firefox_users with conditions: \"[and, [or, [not, [or, {name='browser_type', type='custom_attribute', match='null', value='firefox'}]]]]\"");
        logbackVerifier.expectMessage(Level.INFO,
            "Audience not_firefox_users evaluated to true");
        logbackVerifier.expectMessage(Level.INFO,
            "Audiences for experiment etag1 collectively evaluated to true");
    }

    /**
     * If the {@link Experiment} contains at least one {@link Audience}, but attributes is empty,
     * then {@link AudienceEvaluator#evaluate(Experiment, UserContext)} should return false.
     */
    @Test
    public void evaluatesTrueIfEvenIfExperimentHasAudiencesButUserSendNullAttributes() {
        Experiment experiment = projectConfig.getExperiments().get(0);
        UserContext userContext = new UserContext.Builder()
            .withProjectConfig(projectConfig)
            .withAttributes(null)
            .build();
        assertTrue(audienceEvaluator.evaluate(experiment, userContext));
        logbackVerifier.expectMessage(Level.DEBUG,
            "Evaluating audiences for experiment \"etag1\": \"[100]\"");
        logbackVerifier.expectMessage(Level.DEBUG,
            "Starting to evaluate audience not_firefox_users with conditions: \"[and, [or, [not, [or, {name='browser_type', type='custom_attribute', match='null', value='firefox'}]]]]\"");
        logbackVerifier.expectMessage(Level.INFO,
            "Audience not_firefox_users evaluated to true");
        logbackVerifier.expectMessage(Level.INFO,
            "Audiences for experiment etag1 collectively evaluated to true");
    }

    /**
     * If the {@link Experiment} contains {@link TypedAudience}, and attributes is valid and true,
     * then {@link AudienceEvaluator#evaluate(Experiment, UserContext)} should return true.
     */
    @Test
    public void evaluatesTrueIfExperimentHasTypedAudiences() {
        Experiment experiment = v4ProjectConfig.getExperiments().get(1);
        Map<String, Boolean> attribute = Collections.singletonMap("booleanKey", true);
        UserContext userContext = new UserContext.Builder()
            .withProjectConfig(v4ProjectConfig)
            .withAttributes(attribute)
            .build();
        assertTrue(audienceEvaluator.evaluate(experiment, userContext));
        logbackVerifier.expectMessage(Level.DEBUG,
            "Evaluating audiences for experiment \"typed_audience_experiment\": \"[or, 3468206643, 3468206644, 3468206646, 3468206645]\"");
        logbackVerifier.expectMessage(Level.DEBUG,
            "Starting to evaluate audience BOOL with conditions: \"[and, [or, [or, {name='booleanKey', type='custom_attribute', match='exact', value=true}]]]\"");
        logbackVerifier.expectMessage(Level.INFO,
            "Audience BOOL evaluated to true");
        logbackVerifier.expectMessage(Level.INFO,
            "Audiences for experiment typed_audience_experiment collectively evaluated to true");
    }

    /**
     * If the attributes satisfies at least one {@link Condition} in an {@link Audience} of the {@link Experiment},
     * then {@link AudienceEvaluator#evaluate(Experiment, UserContext)} should return true.
     */
    @Test
    public void evaluatesTrueIfUserSatisfiesAnAudience() {
        Experiment experiment = projectConfig.getExperiments().get(0);
        Map<String, String> attributes = Collections.singletonMap("browser_type", "chrome");
        UserContext userContext = new UserContext.Builder()
            .withProjectConfig(projectConfig)
            .withAttributes(attributes)
            .build();
        assertTrue(audienceEvaluator.evaluate(experiment, userContext));
        logbackVerifier.expectMessage(Level.DEBUG,
            "Evaluating audiences for experiment \"etag1\": \"[100]\"");
        logbackVerifier.expectMessage(Level.DEBUG,
            "Starting to evaluate audience not_firefox_users with conditions: \"[and, [or, [not, [or, {name='browser_type', type='custom_attribute', match='null', value='firefox'}]]]]\"");
        logbackVerifier.expectMessage(Level.INFO,
            "Audience not_firefox_users evaluated to true");
        logbackVerifier.expectMessage(Level.INFO,
            "Audiences for experiment etag1 collectively evaluated to true");
    }

    /**
     * If the attributes satisfies no {@link Condition} of any {@link Audience} of the {@link Experiment},
     * then {@link AudienceEvaluator#evaluate(Experiment, UserContext)} should return false.
     */
    @Test
    public void evaluateFalseIfUserDoesNotSatisfyAnyAudiences() {
        Experiment experiment = projectConfig.getExperiments().get(0);
        Map<String, String> attributes = Collections.singletonMap("browser_type", "firefox");
        UserContext userContext = new UserContext.Builder()
            .withProjectConfig(projectConfig)
            .withAttributes(attributes)
            .build();
        assertFalse(audienceEvaluator.evaluate(experiment, userContext));
        logbackVerifier.expectMessage(Level.DEBUG,
            "Evaluating audiences for experiment \"etag1\": \"[100]\"");
        logbackVerifier.expectMessage(Level.DEBUG,
            "Starting to evaluate audience not_firefox_users with conditions: \"[and, [or, [not, [or, {name='browser_type', type='custom_attribute', match='null', value='firefox'}]]]]\"");
        logbackVerifier.expectMessage(Level.INFO,
            "Audience not_firefox_users evaluated to false");
        logbackVerifier.expectMessage(Level.INFO,
            "Audiences for experiment etag1 collectively evaluated to false");
    }

    /**
     * If there are audiences with attributes on the experiment, but one of the attribute values is null,
     * they must explicitly pass in null in order for us to evaluate this. Otherwise we will say they do not match.
     */
    @Test
    public void isUserInExperimentHandlesNullValue() {
        Experiment experiment = v4ProjectConfig.getExperimentKeyMapping().get(EXPERIMENT_WITH_MALFORMED_AUDIENCE_KEY);
        Map<String, String> satisfiesFirstCondition = Collections.singletonMap(ATTRIBUTE_NATIONALITY_KEY,
            AUDIENCE_WITH_MISSING_VALUE_VALUE);
        Map<String, String> nonMatchingMap = Collections.singletonMap(ATTRIBUTE_NATIONALITY_KEY, "American");
        UserContext userContext = new UserContext.Builder()
            .withProjectConfig(v4ProjectConfig)
            .withAttributes(satisfiesFirstCondition)
            .build();
        assertTrue(audienceEvaluator.evaluate(experiment, userContext));
        userContext = new UserContext.Builder()
            .withProjectConfig(v4ProjectConfig)
            .withAttributes(nonMatchingMap)
            .build();
        assertFalse(audienceEvaluator.evaluate(experiment, userContext));
    }

    /**
     * Audience will evaluate null when condition value is null and attribute value passed is also null
     */
    @Test
    public void isUserInExperimentHandlesNullValueAttributesWithNull() {
        Experiment experiment = v4ProjectConfig.getExperimentKeyMapping().get(EXPERIMENT_WITH_MALFORMED_AUDIENCE_KEY);
        Map<String, String> attributesWithNull = Collections.singletonMap(ATTRIBUTE_NATIONALITY_KEY, null);
        UserContext userContext = new UserContext.Builder()
            .withProjectConfig(v4ProjectConfig)
            .withAttributes(attributesWithNull)
            .build();
        assertFalse(audienceEvaluator.evaluate(experiment, userContext));
        logbackVerifier.expectMessage(Level.DEBUG,
            "Starting to evaluate audience audience_with_missing_value with conditions: \"[and, [or, [or, {name='nationality', type='custom_attribute', match='null', value='English'}, {name='nationality', type='custom_attribute', match='null', value=null}]]]\"");
        logbackVerifier.expectMessage(Level.WARN,
            "Audience condition \"{name='nationality', type='custom_attribute', match='null', value=null}\" has an unexpected value type. You may need to upgrade to a newer release of the Optimizely SDK");
        logbackVerifier.expectMessage(Level.INFO,
            "Audience audience_with_missing_value evaluated to null");
        logbackVerifier.expectMessage(Level.INFO,
            "Audiences for experiment experiment_with_malformed_audience collectively evaluated to null");
    }

    /**
     * Audience will evaluate null when condition value is null
     */
    @Test
    public void isUserInExperimentHandlesNullConditionValue() {
        Experiment experiment = v4ProjectConfig.getExperimentKeyMapping().get(EXPERIMENT_WITH_MALFORMED_AUDIENCE_KEY);
        Map<String, String> attributesEmpty = Collections.emptyMap();
        UserContext userContext = new UserContext.Builder()
            .withProjectConfig(v4ProjectConfig)
            .withAttributes(attributesEmpty)
            .build();
        // It should explicitly be set to null otherwise we will return false on empty maps
        assertFalse(audienceEvaluator.evaluate(experiment, userContext));

        logbackVerifier.expectMessage(Level.DEBUG,
            "Starting to evaluate audience audience_with_missing_value with conditions: \"[and, [or, [or, {name='nationality', type='custom_attribute', match='null', value='English'}, {name='nationality', type='custom_attribute', match='null', value=null}]]]\"");
        logbackVerifier.expectMessage(Level.WARN,
            "Audience condition \"{name='nationality', type='custom_attribute', match='null', value=null}\" has an unexpected value type. You may need to upgrade to a newer release of the Optimizely SDK");
        logbackVerifier.expectMessage(Level.INFO,
            "Audience audience_with_missing_value evaluated to null");
        logbackVerifier.expectMessage(Level.INFO,
            "Audiences for experiment experiment_with_malformed_audience collectively evaluated to null");
    }
}