package org.thaliproject.p2p.btconnectorlib;

import android.content.Context;
import android.test.mock.MockContext;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

public class ConnectionManagerTest {

    Context context;

    @org.junit.Before
    public void setUp() {
        context = new MockContext();
        assertThat(context, is(notNullValue()));
    }

    @org.junit.After
    public void tearDown() throws Exception {

    }

    @org.junit.Test
    public void testGetState() throws Exception {


    }
}