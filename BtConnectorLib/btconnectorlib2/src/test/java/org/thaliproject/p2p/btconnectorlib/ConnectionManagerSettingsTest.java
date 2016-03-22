package org.thaliproject.p2p.btconnectorlib;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

public class ConnectionManagerSettingsTest {

    @Mock Context fakeContext;
    @Mock SharedPreferences fakeSharedPreferences;
    ConnectionManagerSettings mConnectionManagerSettings;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testGetInstance() throws Exception {
        mConnectionManagerSettings = ConnectionManagerSettings.getInstance(fakeContext,
                fakeSharedPreferences);
        assertThat(mConnectionManagerSettings, is(notNullValue()));
    }

}