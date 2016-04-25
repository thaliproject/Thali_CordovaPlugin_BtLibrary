package org.thaliproject.p2p.btconnectorlib.utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class CommonUtilsTest {

    @Test
    public void testIsNonEmptyString() throws Exception {

        // check null
        assertThat("Should return false if null provided as a string",
                CommonUtils.isNonEmptyString(null), is(false));

        // check empty
        assertThat("Should return false if empty string provided",
                CommonUtils.isNonEmptyString(""), is(false));

        // check some text
        assertThat("Should return true if non empty string provided",
                CommonUtils.isNonEmptyString("some text"), is(true));
    }

    @Test
    public void testByteArrayToHexString() throws Exception {

        class ExpOut {
            String out;
            String outWithSpaces;

            ExpOut(String o1, String o2) {
                out = o1;
                outWithSpaces = o2;
            }
        }

        // expectedResults
        Map<String, ExpOut> expectedResults = new HashMap<>();
        expectedResults.put("", new ExpOut(null, null));
        expectedResults.put("testa", new ExpOut("7465737461", "74 65 73 74 61"));
        expectedResults.put("testb", new ExpOut("7465737462", "74 65 73 74 62"));
        expectedResults.put("testc", new ExpOut("7465737463", "74 65 73 74 63"));
        expectedResults.put("testd", new ExpOut("7465737464", "74 65 73 74 64"));
        expectedResults.put("testtest", new ExpOut("7465737474657374", "74 65 73 74 74 65 73 74"));

        for (Map.Entry<String, ExpOut> entry : expectedResults.entrySet()) {

            String result = CommonUtils.byteArrayToHexString(entry.getKey().getBytes(), false);
            String resultWithSpaces = CommonUtils.byteArrayToHexString(entry.getKey().getBytes(), true);

            assertThat("Should return proper hex string",
                    entry.getValue().out, is(result));

            assertThat("Should return proper hex string with spaces",
                    entry.getValue().outWithSpaces, is(resultWithSpaces));

        }
    }
}