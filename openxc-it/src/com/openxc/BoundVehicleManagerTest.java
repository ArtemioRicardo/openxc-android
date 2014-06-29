package com.openxc;

import java.net.URI;

import junit.framework.Assert;
import android.content.Intent;
import android.test.ServiceTestCase;
import android.test.suitebuilder.annotation.MediumTest;

import com.openxc.measurements.EngineSpeed;
import com.openxc.measurements.Measurement;
import com.openxc.measurements.SteeringWheelAngle;
import com.openxc.measurements.TurnSignalStatus;
import com.openxc.measurements.UnrecognizedMeasurementTypeException;
import com.openxc.measurements.VehicleSpeed;
import com.openxc.messages.NamedVehicleMessage;
import com.openxc.messages.VehicleMessage;
import com.openxc.remote.VehicleService;
import com.openxc.remote.VehicleServiceException;
import com.openxc.sinks.BaseVehicleDataSink;
import com.openxc.sinks.VehicleDataSink;
import com.openxc.sources.DataSourceException;
import com.openxc.sources.TestSource;

public class BoundVehicleManagerTest extends ServiceTestCase<VehicleManager> {
    VehicleManager service;
    VehicleSpeed speedReceived;
    SteeringWheelAngle steeringAngleReceived;
    String receivedMessageId;
    TestSource source = new TestSource();

    VehicleSpeed.Listener speedListener = new VehicleSpeed.Listener() {
        public void receive(Measurement measurement) {
            speedReceived = (VehicleSpeed) measurement;
        }
    };

    SteeringWheelAngle.Listener steeringWheelListener =
            new SteeringWheelAngle.Listener() {
        public void receive(Measurement measurement) {
            steeringAngleReceived = (SteeringWheelAngle) measurement;
        }
    };

    public BoundVehicleManagerTest() {
        super(VehicleManager.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        speedReceived = null;
        steeringAngleReceived = null;

        // if the service is already running (and thus may have old data
        // cached), kill it.
        getContext().stopService(new Intent(getContext(),
                    VehicleService.class));
    }

    // Due to bugs and or general crappiness in the ServiceTestCase, you will
    // run into many unexpected problems if you start the service in setUp - see
    // this blog post for more details:
    // http://convales.blogspot.de/2012/07/never-start-or-shutdown-service-in.html
    private void prepareServices() {
        Intent startIntent = new Intent();
        startIntent.setClass(getContext(), VehicleManager.class);
        service = ((VehicleManager.VehicleBinder)
                bindService(startIntent)).getService();
        service.waitUntilBound();
        service.addSource(source);
    }

    @Override
    protected void tearDown() throws Exception {
        if(source != null) {
            source.stop();
        }
        super.tearDown();
    }

    @MediumTest
    public void testGetNoData() throws UnrecognizedMeasurementTypeException {
        prepareServices();
        try {
            service.get(EngineSpeed.class);
        } catch(NoValueException e) {
            return;
        }
        Assert.fail("Expected a NoValueException");
    }

    @MediumTest
    public void testAddListener() throws VehicleServiceException,
            UnrecognizedMeasurementTypeException {
        prepareServices();
        service.addListener(VehicleSpeed.class, speedListener);
        source.inject(VehicleSpeed.ID, 42.0);
        assertNotNull(speedReceived);
    }

    @MediumTest
    public void testCustomSink() throws DataSourceException {
        prepareServices();
        assertNull(receivedMessageId);
        service.addSink(mCustomSink);
        source.inject(VehicleSpeed.ID, 42.0);
        assertNotNull(receivedMessageId);
        service.removeSink(mCustomSink);
        receivedMessageId = null;
        source.inject(VehicleSpeed.ID, 42.0);
        assertNull(receivedMessageId);
    }

    @MediumTest
    public void testAddListenersTwoMeasurements()
            throws VehicleServiceException,
            UnrecognizedMeasurementTypeException {
        prepareServices();
        service.addListener(VehicleSpeed.class, speedListener);
        service.addListener(SteeringWheelAngle.class, steeringWheelListener);
        source.inject(VehicleSpeed.ID, 42.0);
        source.inject(SteeringWheelAngle.ID, 12.1);
        TestUtils.pause(5);
        assertNotNull(steeringAngleReceived);
        assertNotNull(speedReceived);
    }

    @MediumTest
    public void testRemoveListener() throws VehicleServiceException,
            UnrecognizedMeasurementTypeException {
        prepareServices();
        service.addListener(VehicleSpeed.class, speedListener);
        source.inject(VehicleSpeed.ID, 42.0);
        service.removeListener(VehicleSpeed.class, speedListener);
        speedReceived = null;
        source.inject(VehicleSpeed.ID, 42.0);
        TestUtils.pause(10);
        assertNull(speedReceived);
    }

    @MediumTest
    public void testRemoveWithoutListening()
            throws VehicleServiceException {
        prepareServices();
        service.removeListener(VehicleSpeed.class, speedListener);
    }

    @MediumTest
    public void testRemoveOneMeasurementListener()
            throws VehicleServiceException,
            UnrecognizedMeasurementTypeException {
        prepareServices();
        service.addListener(VehicleSpeed.class, speedListener);
        service.addListener(SteeringWheelAngle.class, steeringWheelListener);
        source.inject(VehicleSpeed.ID, 42.0);
        service.removeListener(VehicleSpeed.class, speedListener);
        speedReceived = null;
        source.inject(VehicleSpeed.ID, 42.0);
        TestUtils.pause(10);
        assertNull(speedReceived);
    }

    @MediumTest
    public void testConsistentAge()
            throws UnrecognizedMeasurementTypeException,
            NoValueException, VehicleServiceException, DataSourceException {
        prepareServices();
        source.inject(VehicleSpeed.ID, 42.0);
        TestUtils.pause(1);
        Measurement measurement = service.get(VehicleSpeed.class);
        long age = measurement.getAge();
        assertTrue("Measurement age (" + age + ") should be > 5ms",
                age > 5);
    }

    @MediumTest
    public void testWrite() throws UnrecognizedMeasurementTypeException {
        prepareServices();
        service.send(new TurnSignalStatus(
                    TurnSignalStatus.TurnSignalPosition.LEFT));
        // TODO how can we actually test that it gets written? might need to do
        // smaller unit tests.
    }

    private VehicleDataSink mCustomSink = new BaseVehicleDataSink() {
        public void receive(VehicleMessage message) {
            receivedMessageId = ((NamedVehicleMessage)message).getName();
        }
    };
}

