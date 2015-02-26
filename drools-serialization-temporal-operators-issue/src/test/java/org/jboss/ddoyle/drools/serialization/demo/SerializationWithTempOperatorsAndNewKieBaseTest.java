package org.jboss.ddoyle.drools.serialization.demo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.drools.core.time.impl.PseudoClockScheduler;
import org.jboss.ddoyle.drools.serialization.demo.listener.RulesFiredAgendaEventListener;
import org.jboss.ddoyle.drools.serialization.demo.model.Event;
import org.jboss.ddoyle.drools.serialization.demo.model.SimpleEvent;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.marshalling.Marshaller;
import org.kie.api.marshalling.ObjectMarshallingStrategy;
import org.kie.api.marshalling.ObjectMarshallingStrategyAcceptor;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.internal.marshalling.MarshallerFactory;

import static org.junit.Assert.*;

/**
 * Tests serialization and de-serialization of KieSession and KieBase with a rulebase that contains temporal operators.
 * 
 * @author <a href="mailto:duncan.doyle@redhat.com">Duncan Doyle</a>
 */
public class SerializationWithTempOperatorsAndNewKieBaseTest {

	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd:HHmmssSSS");

	/**
	 * Inserts 2 events, serializes the session, de-serializes the session with the same KieBase and inserts a next event.
	 * <p/>
	 * Rule One should fire 3 times, once for each event.
	 * <p/>
	 * Rule Two should fire 1 time, only for event with timestamp "20150223:090005000", because the event-3 is 16 seconds after event-2,
	 * which is more than 10 seconds.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testSameKieBase() throws Exception {
		RulesFiredAgendaEventListener testListener = new RulesFiredAgendaEventListener();

		KieServices kieServices = KieServices.Factory.get();
		KieContainer kieContainer = kieServices.newKieClasspathContainer();

		// Grabbing the default KieSession.
		KieSession kieSession = kieContainer.newKieSession();
		kieSession.addEventListener(testListener);

		List<SimpleEvent> firstEvents = getFirstSimpleEvents();
		for (SimpleEvent nextSimpleEvent : firstEvents) {
			insertAndAdvance(kieSession, nextSimpleEvent);
			kieSession.fireAllRules();
		}

		// Now Serialize, de-serialize, add third event and execute.
		KieSession newKieSession = serializeAndDeserializeKieSession(kieSession, null);
		newKieSession.addEventListener(testListener);
		kieSession.dispose();

		List<SimpleEvent> nextEvents = getSecondSimpleEvents();
		for (SimpleEvent nextSimpleEvent : nextEvents) {
			insertAndAdvance(newKieSession, nextSimpleEvent);
			newKieSession.fireAllRules();
		}
		newKieSession.dispose();

		assertEquals(3, testListener.getNrOfRulesFired("org.jboss.ddoyle.drools.cep.sample" + "-" + "SimpleTestRule-One"));
		assertEquals(1, testListener.getNrOfRulesFired("org.jboss.ddoyle.drools.cep.sample" + "-" + "SimpleTestRule-Two"));
	}

	/**
	 * Inserts 2 events, serializes the session, de-serializes the session and inserts a next event.
	 * The session is de-serialized with a KieSession with one new rule, which should not influence the other rules. 
	 * 
	 * <p/>
	 * Rule One should fire 3 times, once for each event.
	 * <p/>
	 * Rule Two should fire 1 time, only for event with timestamp "20150223:090005000", because the event-3 is 16 seconds after event-2,
	 * which is more than 10 seconds.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testNewKieBase() throws Exception {
		RulesFiredAgendaEventListener testListener = new RulesFiredAgendaEventListener();

		KieServices kieServices = KieServices.Factory.get();
		KieContainer kieContainer = kieServices.newKieClasspathContainer();

		// Grabbing the default KieSession.
		KieSession kieSession = kieContainer.newKieSession();
		kieSession.addEventListener(testListener);

		List<SimpleEvent> firstEvents = getFirstSimpleEvents();
		for (SimpleEvent nextSimpleEvent : firstEvents) {
			insertAndAdvance(kieSession, nextSimpleEvent);
			kieSession.fireAllRules();
		}

		// Now Serialize, de-serialize, add third event and execute.
		KieBase newKieBase = kieContainer.getKieBase("newRules");
		KieSession newKieSession = serializeAndDeserializeKieSession(kieSession, newKieBase);
		newKieSession.addEventListener(testListener);
		kieSession.dispose();

		List<SimpleEvent> nextEvents = getSecondSimpleEvents();
		for (SimpleEvent nextSimpleEvent : nextEvents) {
			insertAndAdvance(newKieSession, nextSimpleEvent);
			newKieSession.fireAllRules();
		}
		newKieSession.dispose();

		assertEquals(3, testListener.getNrOfRulesFired("org.jboss.ddoyle.drools.cep.sample" + "-" + "SimpleTestRule-One"));
		assertEquals(1, testListener.getNrOfRulesFired("org.jboss.ddoyle.drools.cep.sample" + "-" + "SimpleTestRule-Two"));
		assertEquals(3, testListener.getNrOfRulesFired("org.jboss.ddoyle.drools.cep.sample" + "-" + "SimpleTestRule-Three"));
	}

	private List<SimpleEvent> getFirstSimpleEvents() throws Exception {
		List<SimpleEvent> simpleEvents = new ArrayList<>();
		simpleEvents.add(new SimpleEvent("1", DATE_FORMAT.parse("20150223:090000000")));
		simpleEvents.add(new SimpleEvent("2", DATE_FORMAT.parse("20150223:090005000")));
		return simpleEvents;
	}

	private List<SimpleEvent> getSecondSimpleEvents() throws Exception {
		List<SimpleEvent> simpleEvents = new ArrayList<>();
		simpleEvents.add(new SimpleEvent("3", DATE_FORMAT.parse("20150223:090021000")));
		return simpleEvents;
	}

	public static void insertAndAdvance(KieSession kieSession, Event event) {
		kieSession.insert(event);
		// Advance the clock if required.
		PseudoClockScheduler clock = kieSession.getSessionClock();
		long advanceTime = event.getTimestamp().getTime() - clock.getCurrentTime();
		if (advanceTime > 0) {
			clock.advanceTime(advanceTime, TimeUnit.MILLISECONDS);
		}
	}

	private KieSession serializeAndDeserializeKieSession(KieSession kieSession, KieBase newKieBase) throws Exception {
		ByteArrayOutputStream outputStream = save(kieSession);
		InputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());
		outputStream.close();
		KieSession newKieSession = load(inputStream, newKieBase);
		inputStream.close();
		return newKieSession;
	}

	private ByteArrayOutputStream save(KieSession kieSession) throws Exception {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

		// First write the KieBase
		ObjectOutputStream oos = new ObjectOutputStream(outputStream);
		oos.writeObject(kieSession.getKieBase());
		// Write the configuration to store the session clock.
		KieSessionConfiguration kieSessionConfiguration = kieSession.getSessionConfiguration();
		oos.writeObject(kieSessionConfiguration);

		Marshaller marshaller = createMarshaller(kieSession.getKieBase());
		marshaller.marshall(outputStream, kieSession);

		return outputStream;
	}

	private KieSession load(InputStream inputStream, KieBase kieBase) throws Exception {
		ObjectInputStream ois = new ObjectInputStream(inputStream);

		// Read the KieBase
		if (kieBase == null) {
			kieBase = (KieBase) ois.readObject();
		} else {
			ois.readObject();
		}

		// Read the KieSessionConfiguration
		KieSessionConfiguration kieSessionConfiguration = (KieSessionConfiguration) ois.readObject();

		Marshaller marshaller = createMarshaller(kieBase);
		KieSession kieSession = marshaller.unmarshall(inputStream, kieSessionConfiguration, null);

		return kieSession;
	}

	private Marshaller createMarshaller(KieBase kieBase) {
		ObjectMarshallingStrategyAcceptor acceptor = MarshallerFactory.newClassFilterAcceptor(new String[] { "*.*" });
		ObjectMarshallingStrategy strategy = MarshallerFactory.newSerializeMarshallingStrategy(acceptor);
		Marshaller marshaller = MarshallerFactory.newMarshaller(kieBase, new ObjectMarshallingStrategy[] { strategy });
		return marshaller;
	}

}
