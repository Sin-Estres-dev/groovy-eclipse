/*******************************************************************************
 * Copyright (c) 2021 jkubitz and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     jkubitz - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.core.tests.compiler.map;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.eclipse.jdt.core.tests.junit.extension.TestCase;
import org.eclipse.jdt.internal.compiler.util.CharArrayHashMap;
import org.eclipse.jdt.internal.compiler.util.CharArrayMap;
import org.eclipse.jdt.internal.compiler.util.CharArrayMapper;
import org.eclipse.jdt.internal.compiler.util.CharDelegateMap;

import junit.framework.Test;
import junit.framework.TestSuite;

public class CharArrayMapperTest extends TestCase {
	public CharArrayMapperTest(String testName) {
		super(testName);
	}
	public static Test suite() {

		TestSuite suite = new TestSuite(CharArrayMapperTest.class.getPackageName());
		suite.addTest(new TestSuite(CharArrayMapperTest.class));
		return suite;
	}
	void testPutNew(CharArrayMapper<String> map, String keyString) {
		int oldSize = map.size();
		String value = "_" + keyString + "_";
		String previous = map.put(keyString.toCharArray(), value);
		int newSize = map.size();
		assertEquals("testPutNew1(" + keyString + ")", null, previous);
		String vinside = map.get(keyString.toCharArray());
		assertEquals("testPutNew2(" + keyString + ")", value, vinside);
		assertEquals("testPutNew3(" + keyString + ")", oldSize+1, newSize);
		assertEquals("testPutNew4(" + keyString + ")", map.keys().size(), newSize);
		assertEquals("testPutNew5(" + keyString + ")", map.values().size(), newSize);
	}

	void testPutExisting(CharArrayMapper<String> map, String keyString) {
		int oldSize = map.size();
		String value = "_new_" + keyString + "_";
		String previous = map.get(keyString.toCharArray());
		String returned = map.put(keyString.toCharArray(), value);
		int newSize = map.size();
		assertEquals("testPutExisting1(" + keyString + ")", previous, returned);
		assertEquals("testPutNew3(" + keyString + ")", oldSize, newSize);
		assertEquals("testPutNew4(" + keyString + ")", map.keys().size(), oldSize);
		assertEquals("testPutNew5(" + keyString + ")", map.values().size(), oldSize);
		map.put(keyString.toCharArray(), previous); // restore
	}

	void testGetExisting(CharArrayMapper<String> map, String keyString) {
		String value = "_" + keyString + "_";
		String vinside = map.get(keyString.toCharArray());
		assertEquals("testGetExisting1(" + keyString + ")", value, vinside);
		assertEquals("testGetExisting2(" + keyString + ")", true, map.values().contains(value));
		assertEquals("testGetExisting3(" + keyString + ")", true,
				map.keys().stream().anyMatch(k -> Arrays.equals(k, keyString.toCharArray())));
	}

	void testGetNonExisting(CharArrayMapper<String> map, String keyString) {
		String value = "_" + keyString + "_";
		String vinside = map.get(keyString.toCharArray());
		assertEquals("testGetNonExisting1(" + keyString + ")", null, vinside);
		assertEquals("testGetNonExisting1(" + keyString + ")", false, map.values().contains(value));
		assertEquals("testGetNonExisting1(" + keyString + ")", false,
				map.keys().stream().anyMatch(k -> Arrays.equals(k, keyString.toCharArray())));
	}


	void testIntList(CharArrayMapper<String> map) {
		assertEquals("empty", Collections.emptyList(), map.keys());
		int N = 100;
		for (int i = 0; i < N; i++) {
			testPutNew(map, "" + i);
			testPutExisting(map, "" + i);
			testGetExisting(map, "" + i);
		}
		for (int i = 0; i < N; i++) {
			testGetExisting(map, "" + i);
		}
		for (int i = N + 1; i < N * 2; i++) {
			testGetNonExisting(map, "" + i);
		}
	}

	void testColliding(CharArrayMapper<String> map) {
		assertEquals("empty", Collections.emptyList(), map.keys());
		for (int[] collision : hashCollisions) {
			int h1 = Arrays.hashCode(("" + collision[0]).toCharArray());
			int h2 = Arrays.hashCode(("" + collision[1]).toCharArray());
			assertEquals("collision(" + collision[0] + "<->" + collision[1] + ")", h1, h2);
			for (int i : collision) {
				testPutNew(map, "" + i);
				testPutExisting(map, "" + i);
				testGetExisting(map, "" + i);
			}
		}
		for (int[] collision : hashCollisions) {
			for (int i : collision) {
				testGetExisting(map, "" + i);
			}
		}
		int N = 100;
		for (int i = N + 1; i < N * 2; i++) {
			testGetNonExisting(map, "" + i);
		}
	}

	public void testCharArrayHashMap() {
		testIntList(new CharArrayHashMap<>(4));
		testColliding(new CharArrayHashMap<>(4));
	}

	public void testCharArrayMap() {
		testIntList(new CharArrayMap<>());
		testColliding(new CharArrayMap<>());
	}

	public void testCharDelegateMap() {
		testIntList(new CharDelegateMap<>());
		testColliding(new CharDelegateMap<>());
	}

	static int[][] hashCollisions = { { 17510, 37760009 }, { 17520, 37760019 }, { 17530, 37760029 },
			{ 17540, 37760039 }, { 17550, 37760049 }, { 17560, 37760059 }, { 17570, 37760069 }, { 17580, 37760079 },
			{ 17590, 37760089 }, { 17610, 37760109 }, { 17620, 37760119 }, { 17630, 37760129 }, { 17640, 37760139 },
			{ 17650, 37760149 }, { 17660, 37760159 }, { 17670, 37760169 }, { 17680, 37760179 }, { 17690, 37760189 },
			{ 17710, 37760209 }, { 17720, 37760219 }, { 17730, 37760229 }, { 17740, 37760239 }, { 17750, 37760249 },
			{ 17760, 37760259 }, { 17770, 37760269 }, { 17780, 37760279 }, { 17790, 37760289 }, { 17810, 37760309 },
			{ 17820, 37760319 }, { 17830, 37760329 }, { 17840, 37760339 }, { 17850, 37760349 }, { 17860, 37760359 },
			{ 17870, 37760369 }, { 17880, 37760379 }, { 17890, 37760389 }, { 17910, 37760409 }, { 17920, 37760419 },
			{ 17930, 37760429 }, { 17940, 37760439 }, { 17950, 37760449 }, { 17960, 37760459 }, { 17970, 37760469 },
			{ 17980, 37760479 }, { 17990, 37760489 }, { 18510, 37761009 }, { 18520, 37761019 }, { 18530, 37761029 },
			{ 18540, 37761039 }, { 18550, 37761049 }, { 18560, 37761059 }, { 18570, 37761069 }, { 18580, 37761079 },
			{ 18590, 37761089 }, { 18610, 37761109 }, { 18620, 37761119 }, { 18630, 37761129 }, { 18640, 37761139 },
			{ 18650, 37761149 }, { 18660, 37761159 }, { 18670, 37761169 }, { 18680, 37761179 }, { 18690, 37761189 },
			{ 18710, 37761209 }, { 18720, 37761219 }, { 18730, 37761229 }, { 18740, 37761239 }, { 18750, 37761249 },
			{ 18760, 37761259 }, { 18770, 37761269 }, { 18780, 37761279 }, { 18790, 37761289 }, { 18810, 37761309 },
			{ 18820, 37761319 }, { 18830, 37761329 }, { 18840, 37761339 }, { 18850, 37761349 }, { 18860, 37761359 },
			{ 18870, 37761369 }, { 18880, 37761379 }, { 18890, 37761389 }, { 18910, 37761409 }, { 18920, 37761419 },
			{ 18930, 37761429 }, { 18940, 37761439 }, { 18950, 37761449 }, { 18960, 37761459 }, { 18970, 37761469 },
			{ 18980, 37761479 }, { 18990, 37761489 }, { 19510, 37762009 }, { 19520, 37762019 }, { 19530, 37762029 },
			{ 19540, 37762039 }, { 19550, 37762049 }, { 19560, 37762059 }, { 19570, 37762069 }, { 19580, 37762079 },
			{ 19590, 37762089 }, { 19610, 37762109 }, { 19620, 37762119 }, { 19630, 37762129 }, { 19640, 37762139 },
			{ 19650, 37762149 }, { 19660, 37762159 }, { 19670, 37762169 }, { 19680, 37762179 }, { 19690, 37762189 },
			{ 19710, 37762209 }, { 19720, 37762219 }, { 19730, 37762229 }, { 19740, 37762239 }, { 19750, 37762249 },
			{ 19760, 37762259 }, { 19770, 37762269 }, { 19780, 37762279 }, { 19790, 37762289 }, { 19810, 37762309 },
			{ 19820, 37762319 }, { 19830, 37762329 }, { 19840, 37762339 }, { 19850, 37762349 }, { 19860, 37762359 },
			{ 19870, 37762369 }, { 19880, 37762379 }, { 19890, 37762389 }, { 19910, 37762409 }, { 19920, 37762419 },
			{ 19930, 37762429 }, { 19940, 37762439 }, { 19950, 37762449 }, { 19960, 37762459 }, { 19970, 37762469 },
			{ 19980, 37762479 }, { 19990, 37762489 }, { 27510, 37770009 }, { 27520, 37770019 }, { 27530, 37770029 },
			{ 27540, 37770039 }, { 27550, 37770049 }, { 27560, 37770059 }, { 27570, 37770069 }, { 27580, 37770079 },
			{ 27590, 37770089 }, { 27610, 37770109 }, { 27620, 37770119 }, { 27630, 37770129 }, { 27640, 37770139 },
			{ 27650, 37770149 }, { 27660, 37770159 }, { 27670, 37770169 }, { 27680, 37770179 }, { 27690, 37770189 },
			{ 27710, 37770209 }, { 27720, 37770219 }, { 27730, 37770229 }, { 27740, 37770239 }, { 27750, 37770249 },
			{ 27760, 37770259 }, { 27770, 37770269 }, { 27780, 37770279 }, { 27790, 37770289 }, { 27810, 37770309 },
			{ 27820, 37770319 }, { 27830, 37770329 }, { 27840, 37770339 }, { 27850, 37770349 }, { 27860, 37770359 },
			{ 27870, 37770369 }, { 27880, 37770379 }, { 27890, 37770389 }, { 27910, 37770409 }, { 27920, 37770419 },
			{ 27930, 37770429 }, { 27940, 37770439 }, { 27950, 37770449 }, { 27960, 37770459 }, { 27970, 37770469 },
			{ 27980, 37770479 }, { 27990, 37770489 }, { 28510, 37771009 }, { 28520, 37771019 }, { 28530, 37771029 },
			{ 28540, 37771039 }, { 28550, 37771049 }, { 28560, 37771059 }, { 28570, 37771069 }, { 28580, 37771079 },
			{ 28590, 37771089 }, { 28610, 37771109 }, { 28620, 37771119 }, { 28630, 37771129 }, { 28640, 37771139 },
			{ 28650, 37771149 }, { 28660, 37771159 }, { 28670, 37771169 }, { 28680, 37771179 }, { 28690, 37771189 },
			{ 28710, 37771209 }, { 28720, 37771219 }, { 28730, 37771229 }, { 28740, 37771239 }, { 28750, 37771249 },
			{ 28760, 37771259 }, { 28770, 37771269 }, { 28780, 37771279 }, { 28790, 37771289 }, { 28810, 37771309 },
			{ 28820, 37771319 }, { 28830, 37771329 }, { 28840, 37771339 }, { 28850, 37771349 }, { 28860, 37771359 },
			{ 28870, 37771369 }, { 28880, 37771379 }, { 28890, 37771389 }, { 28910, 37771409 }, { 28920, 37771419 },
			{ 28930, 37771429 }, { 28940, 37771439 }, { 28950, 37771449 }, { 28960, 37771459 }, { 28970, 37771469 },
			{ 28980, 37771479 }, { 28990, 37771489 }, { 29510, 37772009 }, { 29520, 37772019 }, { 29530, 37772029 },
			{ 29540, 37772039 }, { 29550, 37772049 }, { 29560, 37772059 }, { 29570, 37772069 }, { 29580, 37772079 },
			{ 29590, 37772089 }, { 29610, 37772109 }, { 29620, 37772119 }, { 29630, 37772129 }, { 29640, 37772139 },
			{ 29650, 37772149 }, { 29660, 37772159 }, { 29670, 37772169 }, { 29680, 37772179 }, { 29690, 37772189 },
			{ 29710, 37772209 }, { 29720, 37772219 }, { 29730, 37772229 }, { 29740, 37772239 }, { 29750, 37772249 },
			{ 29760, 37772259 }, { 29770, 37772269 }, { 29780, 37772279 }, { 29790, 37772289 }, { 29810, 37772309 },
			{ 29820, 37772319 }, { 29830, 37772329 }, { 29840, 37772339 }, { 29850, 37772349 }, { 29860, 37772359 },
			{ 29870, 37772369 }, { 29880, 37772379 }, { 29890, 37772389 }, { 29910, 37772409 }, { 29920, 37772419 },
			{ 29930, 37772429 }, { 29940, 37772439 }, { 29950, 37772449 }, { 29960, 37772459 }, { 29970, 37772469 },
			{ 29980, 37772479 }, { 29990, 37772489 }, { 37510, 37780009 }, { 37520, 37780019 }, { 37530, 37780029 },
			{ 37540, 37780039 }, { 37550, 37780049 }, { 37560, 37780059 }, { 37570, 37780069 }, { 37580, 37780079 },
			{ 37590, 37780089 }, { 37610, 37780109 }, { 37620, 37780119 }, { 37630, 37780129 }, { 37640, 37780139 },
			{ 37650, 37780149 }, { 37660, 37780159 }, { 37670, 37780169 }, { 37680, 37780179 }, { 37690, 37780189 },
			{ 37710, 37780209 }, { 37720, 37780219 }, { 37730, 37780229 }, { 37740, 37780239 }, { 37750, 37780249 },			{ 37760, 37780259 }, { 37770, 37780269 }, { 37780, 37780279 }, { 37790, 37780289 }, { 37810, 37780309 },
			{ 37820, 37780319 }, { 37830, 37780329 }, { 37840, 37780339 }, { 37850, 37780349 }, { 37860, 37780359 },
			{ 37870, 37780369 }, { 37880, 37780379 }, { 37890, 37780389 }, { 37910, 37780409 }, { 37920, 37780419 },
			{ 37930, 37780429 }, { 37940, 37780439 }, { 37950, 37780449 }, { 37960, 37780459 }, { 37970, 37780469 },
			{ 37980, 37780479 }, { 37990, 37780489 }, { 38510, 37781009 }, { 38520, 37781019 }, { 38530, 37781029 },
			{ 38540, 37781039 }, { 38550, 37781049 }, { 38560, 37781059 }, { 38570, 37781069 }, { 38580, 37781079 },
			{ 38590, 37781089 }, { 38610, 37781109 }, { 38620, 37781119 }, { 38630, 37781129 }, { 38640, 37781139 },
			{ 38650, 37781149 }, { 38660, 37781159 }, { 38670, 37781169 }, { 38680, 37781179 }, { 38690, 37781189 },
			{ 38710, 37781209 }, { 38720, 37781219 }, { 38730, 37781229 }, { 38740, 37781239 }, { 38750, 37781249 },
			{ 38760, 37781259 }, { 38770, 37781269 }, { 38780, 37781279 }, { 38790, 37781289 }, { 38810, 37781309 },
			{ 38820, 37781319 }, { 38830, 37781329 }, { 38840, 37781339 }, { 38850, 37781349 }, { 38860, 37781359 },
			{ 38870, 37781369 }, { 38880, 37781379 }, { 38890, 37781389 }, { 38910, 37781409 }, { 38920, 37781419 },
			{ 38930, 37781429 }, { 38940, 37781439 }, { 38950, 37781449 }, { 38960, 37781459 }, { 38970, 37781469 },
			{ 38980, 37781479 }, { 38990, 37781489 }, { 39510, 37782009 }, { 39520, 37782019 }, { 39530, 37782029 },
			{ 39540, 37782039 }, { 39550, 37782049 }, { 39560, 37782059 }, { 39570, 37782069 }, { 39580, 37782079 },
			{ 39590, 37782089 }, { 39610, 37782109 }, { 39620, 37782119 }, { 39630, 37782129 }, { 39640, 37782139 },
			{ 39650, 37782149 }, { 39660, 37782159 }, { 39670, 37782169 }, { 39680, 37782179 }, { 39690, 37782189 },
			{ 39710, 37782209 }, { 39720, 37782219 }, { 39730, 37782229 }, { 39740, 37782239 }, { 39750, 37782249 },
			{ 39760, 37782259 }, { 39770, 37782269 }, { 39780, 37782279 }, { 39790, 37782289 }, { 39810, 37782309 },
			{ 39820, 37782319 }, { 39830, 37782329 }, { 39840, 37782339 }, { 39850, 37782349 }, { 39860, 37782359 },
			{ 39870, 37782369 }, { 39880, 37782379 }, { 39890, 37782389 }, { 39910, 37782409 }, { 39920, 37782419 },
			{ 39930, 37782429 }, { 39940, 37782439 }, { 39950, 37782449 }, { 39960, 37782459 }, { 39970, 37782469 },
			{ 39980, 37782479 }, { 39990, 37782489 }, { 47510, 37790009 }, { 47520, 37790019 }, { 47530, 37790029 },
			{ 47540, 37790039 }, { 47550, 37790049 }, { 47560, 37790059 }, { 47570, 37790069 }, { 47580, 37790079 },
			{ 47590, 37790089 }, { 47610, 37790109 }, { 47620, 37790119 }, { 47630, 37790129 }, { 47640, 37790139 },
			{ 47650, 37790149 }, { 47660, 37790159 }, { 47670, 37790169 }, { 47680, 37790179 }, { 47690, 37790189 },
			{ 47710, 37790209 }, { 47720, 37790219 }, { 47730, 37790229 }, { 47740, 37790239 }, { 47750, 37790249 },
			{ 47760, 37790259 }, { 47770, 37790269 }, { 47780, 37790279 }, { 47790, 37790289 }, { 47810, 37790309 },
			{ 47820, 37790319 }, { 47830, 37790329 }, { 47840, 37790339 }, { 47850, 37790349 }, { 47860, 37790359 },
			{ 47870, 37790369 }, { 47880, 37790379 }, { 47890, 37790389 }, { 47910, 37790409 }, { 47920, 37790419 },
			{ 47930, 37790429 }, { 47940, 37790439 }, { 47950, 37790449 }, { 47960, 37790459 }, { 47970, 37790469 },
			{ 47980, 37790479 }, { 47990, 37790489 }, { 48510, 37791009 }, { 48520, 37791019 }, { 48530, 37791029 },
			{ 48540, 37791039 }, { 48550, 37791049 }, { 48560, 37791059 }, { 48570, 37791069 }, { 48580, 37791079 },
			{ 48590, 37791089 }, { 48610, 37791109 }, { 48620, 37791119 }, { 48630, 37791129 }, { 48640, 37791139 },
			{ 48650, 37791149 }, { 48660, 37791159 }, { 48670, 37791169 }, { 48680, 37791179 }, { 48690, 37791189 },
			{ 48710, 37791209 }, { 48720, 37791219 }, { 48730, 37791229 }, { 48740, 37791239 }, { 48750, 37791249 },
			{ 48760, 37791259 }, { 48770, 37791269 }, { 48780, 37791279 }, { 48790, 37791289 }, { 48810, 37791309 },
			{ 48820, 37791319 }, { 48830, 37791329 }, { 48840, 37791339 }, { 48850, 37791349 }, { 48860, 37791359 },
			{ 48870, 37791369 }, { 48880, 37791379 }, { 48890, 37791389 }, { 48910, 37791409 }, { 48920, 37791419 },
			{ 48930, 37791429 }, { 48940, 37791439 }, { 48950, 37791449 }, { 48960, 37791459 }, { 48970, 37791469 },
			{ 48980, 37791479 }, { 48990, 37791489 }, { 49510, 37792009 }, { 49520, 37792019 }, { 49530, 37792029 },
			{ 49540, 37792039 }, { 49550, 37792049 }, { 49560, 37792059 }, { 49570, 37792069 }, { 49580, 37792079 },
			{ 49590, 37792089 }, { 49610, 37792109 }, { 49620, 37792119 }, { 49630, 37792129 }, { 49640, 37792139 },
			{ 49650, 37792149 }, { 49660, 37792159 }, { 49670, 37792169 }, { 49680, 37792179 }, { 49690, 37792189 },
			{ 49710, 37792209 }, { 49720, 37792219 }, { 49730, 37792229 }, { 49740, 37792239 }, { 49750, 37792249 },
			{ 49760, 37792259 }, { 49770, 37792269 }, { 49780, 37792279 }, { 49790, 37792289 }, { 49810, 37792309 },
			{ 49820, 37792319 }, { 49830, 37792329 }, { 49840, 37792339 }, { 49850, 37792349 }, { 49860, 37792359 },
			{ 49870, 37792369 }, { 49880, 37792379 }, { 49890, 37792389 }, { 49910, 37792409 }, { 49920, 37792419 },
			{ 49930, 37792429 }, { 49940, 37792439 }, { 49950, 37792449 }, { 49960, 37792459 }, { 49970, 37792469 },
			{ 49980, 37792479 }, { 49990, 37792489 } };

	/* calculate hashCollisions used above: */
	public static void main(String[] args) {
		int N = 100000000;
		HashMap<Integer, List<Integer>> hashCollisions_ = new HashMap<>();
		for (int i = 0; i < N; i++) {
			char[] key = ("" + i).toCharArray();
			Integer hashCode = Arrays.hashCode(key);
			List<Integer> l = hashCollisions_.computeIfAbsent(hashCode, h -> new ArrayList<>());
			l.add(i);
			if (l.size() > 1) {
				System.out.println(l.size() + " collissions:" + hashCode + "->" + l);
			}
		}
	}
}
