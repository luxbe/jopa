package cz.cvut.kbss.jopa.sessions;

import cz.cvut.kbss.jopa.environment.*;
import cz.cvut.kbss.jopa.model.descriptors.Descriptor;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.jopa.environment.utils.TestEnvironmentUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.URI;
import java.util.*;
import java.util.Map.Entry;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class ChangeManagerTest {

	private static final URI DEFAULT_CONTEXT = URI.create("http://defaultContext");

	private static OWLClassA testA;
	private static OWLClassD testD;
	private static OWLClassC testC;
	private static OWLClassA testAClone;
	private static OWLClassD testDClone;
    private OWLClassC testCClone;
	private static OWLClassB testB;
	private static OWLClassB testBClone;
	private static Set<String> typesCollection;
	private static TestEntity primitivesTest;
	private static Descriptor defaultDescriptor;

    @Mock
    private MetamodelProvider providerMock;

	private ChangeManager manager;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		initAs();
		initBs();
		initC();
		initDs();
		typesCollection = new HashSet<>();
		for (int i = 0; i < 10; i++) {
			typesCollection.add(Integer.toString(i));
		}
        OWLClassF testF = new OWLClassF();
		testF.setUri(URI.create("http://testF"));
		primitivesTest = new TestEntity();
		primitivesTest.setId(0);
		defaultDescriptor = new EntityDescriptor(DEFAULT_CONTEXT);
	}

	private static void initAs() {
		testA = new OWLClassA();
		final URI uri = URI.create("http://testA");
		testA.setUri(uri);
		testA.setStringAttribute("TestStringAttribute");
		testAClone = new OWLClassA();
		testAClone.setUri(uri);
	}

	private static void initBs() {
		testB = new OWLClassB();
		testB.setUri(URI.create("http://testB"));
		testB.setStringAttribute("someString");
		final Map<String, Set<String>> props = new HashMap<>();
		props.put("propertyOne", Collections.singleton("valueOne"));
		props.put("propertyTwo", Collections.singleton("valueTwo"));
		final Set<String> multiProp = new HashSet<>();
		multiProp.add("valueThree");
		multiProp.add("valueFour");
		props.put("propertyThree", multiProp);
		testB.setProperties(props);
		testBClone = new OWLClassB();
		testBClone.setUri(testB.getUri());
		testBClone.setStringAttribute(testB.getStringAttribute());
	}

	private static void initC() {
		testC = new OWLClassC();
		final URI pkC = URI.create("http://testC");
		testC.setUri(pkC);
		List<OWLClassA> refList = new ArrayList<>(10);
		for (int i = 0; i < 10; i++) {
			OWLClassA a = new OWLClassA();
			URI pkA = URI.create("http://testARef" + (i + 1));
			a.setUri(pkA);
			a.setStringAttribute("stringForA" + (i + 1));
			refList.add(a);
		}
		testC.setReferencedList(refList);
	}

	private static void initDs() {
		final URI uri2 = URI.create("http://referencedA");
		final OWLClassA refA = new OWLClassA();
		refA.setUri(uri2);
		refA.setStringAttribute("att");
		testD = new OWLClassD();
		final URI uri3 = URI.create("http://testD");
		testD.setUri(uri3);
		testD.setOwlClassA(refA);
		testDClone = new OWLClassD();
		testDClone.setUri(uri3);
	}

	@Before
	public void setup() {
        MockitoAnnotations.initMocks(this);
		manager = new ChangeManagerImpl(providerMock);
        when(providerMock.isTypeManaged(any(Class.class))).thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                final Class<?> cls = (Class<?>) invocation.getArguments()[0];
                return TestEnvironmentUtils.getManagedTypes().contains(cls);
            }
        });
		testAClone.setStringAttribute(null);
		testAClone.setTypes(null);
		testA.setTypes(null);
		final Map<String, Set<String>> cloneProps = new HashMap<>();
		for (Entry<String, Set<String>> e : testB.getProperties().entrySet()) {
			final Set<String> set = new HashSet<>();
			for (String s : e.getValue()) {
				set.add(s);
			}
			cloneProps.put(e.getKey(), set);
		}
		testBClone.setProperties(cloneProps);
		testCClone = new OWLClassC();
		testCClone.setUri(testC.getUri());
		final List<OWLClassA> clonedList = new ArrayList<>(testC.getReferencedList().size());
		for (OWLClassA a : testC.getReferencedList()) {
			final OWLClassA newA = new OWLClassA();
			newA.setUri(a.getUri());
			newA.setStringAttribute(a.getStringAttribute());
			clonedList.add(newA);
		}
		testCClone.setReferencedList(clonedList);
	}

	@Test
	public void testNullHasChanges() {
		assertFalse(manager.hasChanges(null, null));
	}

	@Test
	public void testHasSimpleChanges() {
		testAClone.setStringAttribute("differentStringAtribute");
		assertTrue(manager.hasChanges(testA, testAClone));
	}

	@Test
	public void testHasNoChanges() {
		testAClone.setStringAttribute(testA.getStringAttribute());
		assertFalse(manager.hasChanges(testA, testA));
	}

	@Test
	public void testReferenceChange() {
		final OWLClassA ref = new OWLClassA();
		final URI uri = URI.create("http://newReferenceA");
		ref.setUri(uri);
		ref.setStringAttribute(testA.getStringAttribute());
		testDClone.setOwlClassA(ref);
		assertTrue(manager.hasChanges(testD, testDClone));
	}

	@Test
	public void testCollectionEmptyHasChange() {
		testAClone.setTypes(typesCollection);
		testA.setTypes(new HashSet<String>());
		assertTrue(manager.hasChanges(testA, testAClone));
	}

	@Test
	public void testCollectionHasChange() {
		testA.setTypes(typesCollection);
		Set<String> changed = new HashSet<>();
		Iterator<String> it = typesCollection.iterator();
		it.next();
		changed.add("111");
		while (it.hasNext()) {
			changed.add(it.next());
		}
		testAClone.setTypes(changed);
		assertTrue(manager.hasChanges(testA, testAClone));
	}

	@Test
	public void testCollectionComplexHasChange() {
		assertEquals(testC.getUri(), testCClone.getUri());
		assertEquals(testC.getReferencedList().get(0).getUri(),
				testCClone.getReferencedList().get(0).getUri());
		assertEquals(testC.getReferencedList().get(5).getStringAttribute(), testCClone
				.getReferencedList().get(5).getStringAttribute());
		testCClone.getReferencedList().get(8).setStringAttribute("changedStringAttribute");
		assertTrue(manager.hasChanges(testC, testCClone));
	}

	@Test
	public void testHasChangeSetFromNull() {
		final OWLClassA a = new OWLClassA();
		a.setUri(testAClone.getUri());
		// The original has string attribute null
		testAClone.setStringAttribute("someString");
		assertTrue(manager.hasChanges(a, testAClone));
	}

	@Test(expected = NullPointerException.class)
	public void testCalculateChangesNull() throws Exception {
		manager.calculateChanges(null);
		fail("This line should not have been reached.");
	}

	@Test
	public void testCalculateChangesNoChanges() throws Exception {
		final OWLClassA a = new OWLClassA();
		a.setUri(testA.getUri());
		a.setStringAttribute(testA.getStringAttribute());
		final ObjectChangeSet chSet = createChangeSet(testA, a);
		final boolean res = manager.calculateChanges(chSet);
		assertFalse(res);
		assertTrue(chSet.getChanges().isEmpty());
	}

	@Test
	public void testCalculateSimpleChanges() throws Exception {
		ObjectChangeSet chSet = createChangeSet(testA, testAClone);
		assertTrue(chSet.getChanges().isEmpty());
		final boolean res = manager.calculateChanges(chSet);
		assertTrue(res);
		assertFalse(chSet.getChanges().isEmpty());
		assertTrue(chSet.getChanges().containsKey("stringAttribute"));
	}

	@Test
	public void testCalculateChangesPrimitives() throws Exception {
		final TestEntity primClone = new TestEntity();
		primClone.setId(primitivesTest.getId() + 10);
		ObjectChangeSet chSet = createChangeSet(primitivesTest, primClone);
		assertTrue(chSet.getChanges().isEmpty());
		final boolean res = manager.calculateChanges(chSet);
		assertTrue(res);
		assertFalse(chSet.getChanges().isEmpty());
		assertTrue(chSet.getChanges().containsKey("id"));
	}

	@Test
	public void testCalculateReferenceChanges() throws Exception {
		testDClone.setOwlClassA(testAClone);
		ObjectChangeSet chSet = createChangeSet(testD, testDClone);
		assertTrue(chSet.getChanges().isEmpty());
		final boolean res = manager.calculateChanges(chSet);
		assertTrue(res);
		assertEquals(1, chSet.getChanges().size());
		assertEquals(testAClone, chSet.getChanges().get(OWLClassD.getOwlClassAField().getName()).getNewValue());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testCalculateCollectionChanges() throws Exception {
		testA.setTypes(typesCollection);
		Set<String> newCollection = new HashSet<>(typesCollection);
		newCollection.remove("8");
		newCollection.add("String");
		testAClone.setTypes(newCollection);
		testAClone.setStringAttribute(testA.getStringAttribute());
		ObjectChangeSet chSet = createChangeSet(testA, testAClone);
		assertTrue(chSet.getChanges().isEmpty());
		final boolean res = manager.calculateChanges(chSet);
		assertTrue(res);
		assertEquals(1, chSet.getChanges().size());
		assertTrue(((Set<String>) chSet.getChanges().
                get(OWLClassA.getTypesField().getName()).getNewValue()).contains("String"));
	}

	@Test
	public void testCalculateMultipleChanges() throws Exception {
		testA.setTypes(typesCollection);
		Set<String> newCollection = new HashSet<>(typesCollection);
		newCollection.remove("8");
		newCollection.add("String");
		testAClone.setTypes(newCollection);
		final URI newURI = URI.create("http://newACloneURI");
		testAClone.setUri(newURI);
		testAClone.setStringAttribute("AnotherStringAttribute");
		ObjectChangeSet chSet = createChangeSet(testA, testAClone);
		assertTrue(chSet.getChanges().isEmpty());
		final boolean res = manager.calculateChanges(chSet);
		assertTrue(res);
		assertEquals(3, chSet.getChanges().size());
		assertTrue(chSet.getChanges().containsKey("stringAttribute"));
		assertTrue(chSet.getChanges().containsKey("types"));
		assertTrue(chSet.getChanges().containsKey("uri"));
	}

	@Test
	public void testCalculateChangesSetNull() throws Exception {
		ObjectChangeSet chSet = createChangeSet(testA, testAClone);
		assertTrue(chSet.getChanges().isEmpty());
		final boolean res = manager.calculateChanges(chSet);
		assertTrue(res);
		assertFalse(chSet.getChanges().isEmpty());
		assertNotNull(chSet);
		assertNull(chSet.getChanges().get("stringAttribute").getNewValue());
	}

	@Test
	public void testCalculateChangeSetFromNull() throws Exception {
		final OWLClassA a = new OWLClassA();
		a.setUri(testAClone.getUri());
		// The original has string attribute null
		testAClone.setStringAttribute("someString");
		final ObjectChangeSet chSet = createChangeSet(a, testAClone);
		final boolean res = manager.calculateChanges(chSet);
		assertTrue(res);
		assertEquals(1, chSet.getChanges().size());
		assertEquals(1, chSet.getChanges().size());
		final ChangeRecord rec = chSet.getChanges().get(OWLClassA.getStrAttField().getName());
		assertNotNull(rec.getNewValue());
	}

	@Test
	public void testCalculateChangesRemoveItemFromReferenceList() throws Exception {
		testCClone.getReferencedList().remove(4);
		ObjectChangeSet chSet = createChangeSet(testC, testCClone);
		assertTrue(chSet.getChanges().isEmpty());
		final boolean res = manager.calculateChanges(chSet);
		assertTrue(res);
		assertTrue(chSet.getChanges().containsKey("referencedList"));
		assertEquals(1, chSet.getChanges().size());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testCalculateChangesAddItemToReferenceList() throws Exception {
		OWLClassA add = new OWLClassA();
		final URI pk = URI.create("http://addedA");
		add.setUri(pk);
		add.setStringAttribute("string");
		testCClone.getReferencedList().add(add);
		ObjectChangeSet chSet = createChangeSet(testC, testCClone);
		assertTrue(chSet.getChanges().isEmpty());
		final boolean res = manager.calculateChanges(chSet);
		assertTrue(res);
		assertEquals(1, chSet.getChanges().size());
		final ChangeRecord r = chSet.getChanges().get(OWLClassC.getRefListField().getName());
		List<OWLClassA> refs = (List<OWLClassA>) r.getNewValue();
		assertEquals(add.getUri(), refs.get(10).getUri());
		assertEquals(add.getStringAttribute(), refs.get(10).getStringAttribute());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testCalculateChangesChangeItemInReferenceList() throws Exception {
		OWLClassA newOne = new OWLClassA();
		final URI pk = URI.create("http://newOne");
		newOne.setUri(pk);
		newOne.setStringAttribute("newOnesString");
		testCClone.getReferencedList().remove(3);
		testCClone.getReferencedList().add(newOne);
		ObjectChangeSet chSet = createChangeSet(testC, testCClone);
		assertTrue(chSet.getChanges().isEmpty());
		final boolean res = manager.calculateChanges(chSet);
		assertTrue(res);
		assertTrue(chSet.getChanges().containsKey("referencedList"));
		assertEquals(1, chSet.getChanges().size());
		final ChangeRecord r = chSet.getChanges().get(OWLClassC.getRefListField().getName());
		List<OWLClassA> refs = (List<OWLClassA>) r.getNewValue();
		assertTrue(refs.contains(newOne));
	}

	@Test
	public void testCalculateChangesMapAddKey() throws Exception {
		testBClone.getProperties().put("newProperty", Collections.singleton("propVal"));
		final ObjectChangeSet chSet = createChangeSet(testB, testBClone);
		final boolean res = manager.calculateChanges(chSet);
		assertTrue(res);
		assertEquals(1, chSet.getChanges().size());
		assertTrue(chSet.getChanges().containsKey("properties"));
	}

	@Test
	public void testCalculateChangesMapChangeValue() throws Exception {
		final String key = testB.getProperties().keySet().iterator().next();
		testBClone.getProperties().put(key, Collections.singleton("propVal"));
		final ObjectChangeSet chSet = createChangeSet(testB, testBClone);
		final boolean res = manager.calculateChanges(chSet);
		assertTrue(res);
		assertEquals(1, chSet.getChanges().size());
		assertTrue(chSet.getChanges().containsKey("properties"));
	}

	@Test
	public void testCalculateChangesMapAddValue() throws Exception {
		final String key = testB.getProperties().keySet().iterator().next();
		testBClone.getProperties().get(key).add("addedValue");
		final ObjectChangeSet chSet = createChangeSet(testB, testBClone);
		final boolean res = manager.calculateChanges(chSet);
		assertTrue(res);
		assertEquals(1, chSet.getChanges().size());
		assertTrue(chSet.getChanges().containsKey("properties"));
	}

	private ObjectChangeSet createChangeSet(Object orig, Object clone) {
		return TestEnvironmentUtils.createObjectChangeSet(orig, clone,
				defaultDescriptor.getContext());
	}

	private static final class TestEntity {

		private int id;

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

	}
}
