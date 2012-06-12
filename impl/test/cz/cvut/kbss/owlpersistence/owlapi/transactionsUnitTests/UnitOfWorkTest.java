package cz.cvut.kbss.owlpersistence.owlapi.transactionsUnitTests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.semanticweb.owlapi.model.IRI;

import cz.cvut.kbss.owlpersistence.owlapi.EntityManagerImpl;
import cz.cvut.kbss.owlpersistence.owlapi.EntityManagerImpl.State;
import cz.cvut.kbss.owlpersistence.owlapi.OWLClassA;
import cz.cvut.kbss.owlpersistence.owlapi.OWLClassB;
import cz.cvut.kbss.owlpersistence.owlapi.OWLClassD;
import cz.cvut.kbss.owlpersistence.owlapi.TestEnvironment;
import cz.cvut.kbss.owlpersistence.sessions.UnitOfWorkImpl;

public class UnitOfWorkTest {

	UnitOfWorkImpl testUOW;
	EntityManagerImpl em;
	OWLClassA testObject;
	OWLClassB testObjectTwo;
	OWLClassD testObjectThree;

	@Before
	public void setUp() {
		this.em = (EntityManagerImpl) TestEnvironment
				.getPersistenceConnector("UnitOfWorkJUnitTest");
		this.testUOW = this.em.getCurrentPersistenceContext();
		final URI pkOne = URI.create("http://testOne");
		this.testObject = new OWLClassA();
		this.testObject.setUri(pkOne);
		this.testObject.setStringAttribute("attribute");
		this.testObject.setTypes(new HashSet<String>());
		final URI pkTwo = URI.create("http://testTwo");
		this.testObjectTwo = new OWLClassB();
		this.testObjectTwo.setUri(pkTwo);
		final URI pkThree = URI.create("http://testThree");
		this.testObjectThree = new OWLClassD();
		this.testObjectThree.setUri(pkThree);
		this.testObjectThree.setOwlClassA(testObject);
		this.em.getTransaction().begin();
		this.em.persist(testObject);
		this.em.persist(testObjectTwo);
		this.em.persist(testObjectThree);
		this.em.getTransaction().commit();
		this.em.find(OWLClassA.class, pkOne);
		this.em.find(OWLClassB.class, pkTwo);
		this.em.find(OWLClassD.class, pkThree);
	}

	@After
	public void tearDown() {
		this.em.close();
	}

	@Test
	public void testReadObjectFromCache() {
		OWLClassA res = this.testUOW.readObject(OWLClassA.class,
				IRI.create(testObject.getUri()));
		assertNotNull(res);
		assertEquals(res.getStringAttribute(), testObject.getStringAttribute());
	}

	@Test
	public void testReadObectWithNull() {
		assertNull(testUOW.readObject(testObject.getClass(), null));
		assertNull(testUOW.readObject(null, testObjectTwo.getUri()));
	}

	@Test
	public void testReadObjectFromOntology() {
		// Clear the cache so the entity will be loaded from the ontology
		this.em.clear();
		OWLClassA res = this.testUOW.readObject(OWLClassA.class,
				IRI.create(testObject.getUri()));
		assertNotNull(res);
		assertEquals(testObject.getUri(), res.getUri());
	}

	@Test
	public void testCalculateChanges() {
		this.em.getTransaction().begin();
		OWLClassB toDelete = em.find(OWLClassB.class,
				IRI.create(testObjectTwo.getUri()));
		assertNotNull(toDelete);
		this.em.remove(toDelete);
		this.em.getTransaction().commit();
		OWLClassB res = em.find(OWLClassB.class,
				IRI.create(testObjectTwo.getUri()));
		assertNull(res);
	}

	@Test
	public void testCalculateNewObjects() {
		OWLClassB newOne = new OWLClassB();
		final URI pk = URI.create("http://testNewOne");
		newOne.setUri(pk);
		newOne.setStringAttribute("testAttributeOne");
		OWLClassB newTwo = new OWLClassB();
		final URI pkTwo = URI.create("http://testNewTwo");
		newTwo.setUri(pkTwo);
		this.em.getTransaction().begin();
		this.em.persist(newOne);
		this.em.persist(newTwo);
		this.em.getTransaction().commit();
		newOne = this.em.find(OWLClassB.class, newOne.getUri());
		newTwo = this.em.find(OWLClassB.class, newTwo.getUri());
		assertTrue(this.testUOW.isObjectManaged(newOne));
		assertTrue(this.testUOW.isObjectManaged(newTwo));
	}

	@Test
	public void testContains() {
		Object tO = this.em.find(testObject.getClass(), testObject.getUri());
		assertTrue(testUOW.contains(tO));
	}

	@Test
	public void testGetState() {
		assertEquals(State.DETACHED, testUOW.getState(testObject));
		Object toRemove = em.find(testObjectTwo.getClass(),
				testObjectTwo.getUri());
		testUOW.removeObject(toRemove);
		assertEquals(State.REMOVED, testUOW.getState(toRemove));
		final OWLClassA stateTest = new OWLClassA();
		final URI pk = URI.create("http://stateTest");
		stateTest.setUri(pk);
		assertEquals(State.NEW, testUOW.getState(stateTest));
	}

	@Test
	public void testGetOriginal() {
		Object tO = this.em.find(testObject.getClass(), testObject.getUri());
		OWLClassA origOne = (OWLClassA) testUOW.getOriginal(tO);
		assertEquals(origOne.getUri(), testObject.getUri());
		OWLClassA origTwo = (OWLClassA) testUOW.getOriginal(tO);
		assertEquals(origOne, origTwo);
	}

	@Test
	public void testGetOriginalNull() {
		assertNull(testUOW.getOriginal(null));
	}

	@Test
	public void testIsObjectNew() {
		final OWLClassA testNew = new OWLClassA();
		final URI pk = URI.create("http://testNewOne");
		testNew.setUri(pk);
		testUOW.registerNewObject(IRI.create(testNew.getUri()), testNew);
		assertTrue(testUOW.isObjectNew(testNew));
	}

	@Test
	public void testIsObjectNewWithNullAndManaged() {
		assertFalse(testUOW.isObjectNew(null));
		assertFalse(testUOW.isObjectNew(testObject));
	}

	@Test
	public void testIsObjectManaged() {
		Object o = em.find(testObjectTwo.getClass(), testObjectTwo.getUri());
		assertTrue(testUOW.isObjectManaged(o));
		assertFalse(testUOW.isObjectManaged(null));
	}

	@Test
	public void testMergeChangesIntoParent() {
		OWLClassA testEntity = new OWLClassA();
		final URI pk = URI.create("http://testEntity");
		testEntity.setUri(pk);
		testEntity.setStringAttribute("testAtt");
		Object toRemove = em.find(testObject.getClass(), testObject.getUri());
		this.em.getTransaction().begin();
		em.persist(testEntity);
		this.em.remove(toRemove);
		this.em.getTransaction().commit();
		testEntity = em.find(testEntity.getClass(), testEntity.getUri());
		final OWLClassA original = (OWLClassA) testUOW.getOriginal(testEntity);
		assertTrue(testUOW.getLiveObjectCache().containsObject(original));
		assertFalse(testUOW.getLiveObjectCache().containsObjectByIRI(
				IRI.create(testObject.getUri())));
	}

	@Test
	public void testRegisterAllObjects() {
		List<Object> testEntities = new ArrayList<Object>();
		testEntities.add(testObjectTwo);
		final OWLClassB tstOne = new OWLClassB();
		final URI pk = URI.create("http://tstOne");
		tstOne.setUri(pk);
		tstOne.setStringAttribute("tstOne");
		testEntities.add(tstOne);
		final OWLClassB tstTwo = new OWLClassB();
		final URI pkTwo = URI.create("http://tstTwo");
		tstTwo.setUri(pkTwo);
		tstTwo.setStringAttribute("tstTwo");
		testEntities.add(tstTwo);
		final int expectedSize = 3;
		Vector<Object> res = this.testUOW.registerAllObjects(testEntities);
		assertEquals(expectedSize, res.size());
	}

	@Test
	public void testRegisterExistingObject() {
		Object o = em.find(testObjectTwo.getClass(), testObjectTwo.getUri());
		OWLClassB orig = (OWLClassB) this.testUOW.getOriginal(o);
		OWLClassB clone = (OWLClassB) this.testUOW.registerExistingObject(orig);
		assertEquals(o, clone);
	}

	/**
	 * This method tests the situation when the Unit of Work has no clone to
	 * originals mapping - it was cleared. This tests the second branch of the
	 * register method.
	 */
	@Test
	public void testRegisterExistingObjectOnCleared() {
		this.em.clear();
		OWLClassA clone = this.em.find(OWLClassA.class,
				IRI.create(testObject.getUri()));
		assertEquals(testObject.getUri(), clone.getUri());
	}

	/**
	 * This method tests the branch where existing object is registered
	 */
	@Test
	public void testRegisterObject() {
		OWLClassA o = this.em.find(OWLClassA.class,
				IRI.create(testObject.getUri()));
		OWLClassA clone = (OWLClassA) this.testUOW.registerObject(o);
		assertEquals(o, clone);
	}

	@Test
	public void testRegisterObjectWithNew() {
		final OWLClassA newOne = new OWLClassA();
		final URI pk = URI.create("http://newOne");
		newOne.setUri(pk);
		newOne.setStringAttribute("str");
		Object clone = this.testUOW.registerObject(newOne);
		assertEquals(newOne, clone);
	}

	@Test
	public void testRemoveObjectFromCache() {
		OWLClassB original = (OWLClassB) this.testUOW
				.getOriginal(testObjectTwo);
		this.testUOW.removeObjectFromCache(original);
		assertFalse(this.testUOW.getLiveObjectCache().containsObject(original));
	}

	@Test
	public void testRegisterNewObject() {
		final OWLClassA newOne = new OWLClassA();
		final URI pk = URI.create("http://newEntity");
		newOne.setUri(pk);
		newOne.setStringAttribute("stringAttributeOne");
		this.testUOW.registerNewObject(IRI.create(pk), newOne);
		assertTrue(testUOW.getNewObjectsCloneToOriginal().containsKey(newOne));
	}

	@Test
	public void testPrimaryKeyAlreadyUsed() {
		final IRI testKey = IRI.create(testObject.getUri());
		assertTrue(testUOW.primaryKeyAlreadyUsed(testKey));
		final IRI testNotUsed = IRI.create("http://notUsed");
		assertFalse(testUOW.primaryKeyAlreadyUsed(testNotUsed));
	}

	@Test
	public void testReleaseUnitOfWork() {
		this.testUOW.release();
		assertTrue(testUOW.getCloneMapping().isEmpty());
		assertFalse(testUOW.isActive());
	}

	@Test
	public void testRemoveObject() {
		Object toRemove = em.find(testObject.getClass(), testObject.getUri());
		this.testUOW.removeObject(toRemove);
		assertTrue(testUOW.getDeletedObjects().containsKey(toRemove));
	}

	@Test
	public void testRemoveNewObject() {
		final OWLClassB newOne = new OWLClassB();
		final URI pk = URI.create("http://testObject");
		newOne.setUri(pk);
		newOne.setStringAttribute("strAtt");
		this.testUOW.registerNewObject(IRI.create(newOne.getUri()), newOne);
		// Now try to remove it
		this.testUOW.removeObject(newOne);
		assertFalse(testUOW.contains(newOne));
	}

	@Test
	public void testUnregisterObject() {
		OWLClassA original = (OWLClassA) this.testUOW.getOriginal(testObject);
		this.testUOW.unregisterObject(testObject);
		assertFalse(testUOW.getLiveObjectCache().containsObject(original));
	}

	@Test
	public void revertObject() {
		OWLClassA clone = em.find(OWLClassA.class, testObject.getUri());
		assertNotNull(clone);
		final String changedAtt = "changedAtt";
		clone.setStringAttribute(changedAtt);
		this.testUOW.revertObject(clone);
		assertEquals(testObject.getStringAttribute(),
				clone.getStringAttribute());
	}

	@Test
	public void revertObjectReference() {
		OWLClassD clone = em.find(OWLClassD.class, testObjectThree.getUri());
		OWLClassA changedRef = new OWLClassA();
		final URI pk = URI.create("http://changedOne");
		changedRef.setStringAttribute("changedAtt");
		changedRef.setUri(pk);
		clone.setOwlClassA(changedRef);
		this.testUOW.revertObject(clone);
		assertEquals(testObject.getUri(), clone.getOwlClassA().getUri());
		assertEquals(testObject.getStringAttribute(), clone.getOwlClassA()
				.getStringAttribute());
		assertNotNull(clone.getOwlClassA().getTypes());
	}
}