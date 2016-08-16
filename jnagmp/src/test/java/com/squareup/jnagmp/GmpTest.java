/*
 * Copyright 2013 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.jnagmp;

import com.squareup.jnagmp.ModPowVectors.TestVector;
import java.math.BigInteger;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static com.squareup.jnagmp.Gmp.exactDivide;
import static com.squareup.jnagmp.Gmp.gcd;
import static com.squareup.jnagmp.Gmp.kronecker;
import static com.squareup.jnagmp.Gmp.modInverse;
import static com.squareup.jnagmp.Gmp.modPowInsecure;
import static com.squareup.jnagmp.Gmp.modPowSecure;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/** Tests {@link Gmp}. */
public class GmpTest {

  public static final ModPowStrategy JAVA = new ModPowStrategy() {
    @Override public BigInteger modPow(BigInteger base, BigInteger exponent, BigInteger modulus) {
      return base.modPow(exponent, modulus);
    }
  };
  public static final ModPowStrategy INSECURE = new ModPowStrategy() {
    @Override public BigInteger modPow(BigInteger base, BigInteger exponent, BigInteger modulus) {
      return modPowInsecure(base, exponent, modulus);
    }
  };
  public static final ModPowStrategy INSECURE_GMP_INTS = new ModPowStrategy() {
    @Override public BigInteger modPow(BigInteger base, BigInteger exponent, BigInteger modulus) {
      return modPowInsecure(new GmpInteger(base), new GmpInteger(exponent),
          new GmpInteger(modulus));
    }
  };
  public static final ModPowStrategy SECURE = new ModPowStrategy() {
    @Override public BigInteger modPow(BigInteger base, BigInteger exponent, BigInteger modulus) {
      return modPowSecure(base, exponent, modulus);
    }
  };
  public static final ModPowStrategy SECURE_GMP_INTS = new ModPowStrategy() {
    @Override public BigInteger modPow(BigInteger base, BigInteger exponent, BigInteger modulus) {
      return modPowSecure(new GmpInteger(base), new GmpInteger(exponent), new GmpInteger(modulus));
    }
  };

  @BeforeClass public static void checkLoaded() {
    Gmp.checkLoaded();
  }

  /** Force GC to verify {@link Gmp#finalize()} cleans up properly without crashing. */
  @AfterClass public static void forceGc() throws InterruptedException {
    Gmp.INSTANCE.remove();
    final AtomicBoolean gcHappened = new AtomicBoolean(false);
    new Object() {
      @Override protected void finalize() throws Throwable {
        super.finalize();
        gcHappened.set(true);
      }
    };
    while (!gcHappened.get()) {
      System.gc();
      Thread.sleep(100);
    }
  }

  interface ModPowStrategy {
    BigInteger modPow(BigInteger base, BigInteger exponent, BigInteger modulus);
  }

  ModPowStrategy strategy;

  private void doTest(TestVector v) {
    assertEquals(v.pResult, strategy.modPow(v.message, v.dp, v.p));
    assertEquals(v.qResult, strategy.modPow(v.message, v.dq, v.q));
    assertNotEquals(v.pResult, strategy.modPow(v.message, v.dp, v.q));
    assertNotEquals(v.pResult, strategy.modPow(v.message, v.dq, v.p));
    assertNotEquals(v.qResult, strategy.modPow(v.message, v.dp, v.q));
    assertNotEquals(v.qResult, strategy.modPow(v.message, v.dq, v.p));
  }

  public long modPow(long base, long exponent, long modulus) {
    return strategy.modPow(BigInteger.valueOf(base), BigInteger.valueOf(exponent),
        BigInteger.valueOf(modulus)).longValue();
  }

  @Test public void testExamplesJava() {
    strategy = JAVA;
    testOddExamples();
    testEvenExamples();
  }

  @Test public void testExamplesInsecure() {
    strategy = INSECURE;
    testOddExamples();
    testEvenExamples();
  }

  @Test public void testExamplesInsecureGmpInts() {
    strategy = INSECURE_GMP_INTS;
    testOddExamples();
    testEvenExamples();
  }

  @Test public void testExamplesSecure() {
    strategy = SECURE;
    testOddExamples();
  }

  @Test public void testExamplesSecureGmpInts() {
    strategy = SECURE_GMP_INTS;
    testOddExamples();
  }

  @Test
  public void testModInverse() {
    assertEquals(BigInteger.valueOf(2),
        modInverse(BigInteger.valueOf(3), BigInteger.valueOf(5)));
    Random rnd = new Random();
    BigInteger m = new BigInteger(1024, rnd).nextProbablePrime();
    for (int i = 0; i < 100; i++) {
      BigInteger x = new BigInteger(1023, rnd);
      assertEquals(x.modInverse(m), modInverse(x, m));
    }
  }

  @Test public void testModInverseSmallExhaustive() {
    for (int val = 10; val >= 0; --val) {
      for (int mod = 10; mod >= -1; --mod) {
        BigInteger bVal = BigInteger.valueOf(val);
        BigInteger bMod = BigInteger.valueOf(mod);
        try {
          BigInteger expected = bVal.modInverse(bMod);
          BigInteger actual = modInverse(bVal, bMod);
          assertEquals(String.format("val %d, mod %d", val, mod) + mod, expected, actual);
        } catch (ArithmeticException e) {
          try {
            modInverse(bVal, bMod);
            fail("ArithmeticException expected");
          } catch (ArithmeticException expected) {
          }
        }
      }
    }
  }

  @Test
  public void testModInverseArithmeticException() {
    try {
      modInverse(BigInteger.ONE, BigInteger.valueOf(-1));
      fail("ArithmeticException expected");
    } catch (ArithmeticException expected) {
    }
    try {
      modInverse(BigInteger.valueOf(3), BigInteger.valueOf(9));
      fail("ArithmeticException expected");
    } catch (ArithmeticException expected) {
    }
  }

  @Test
  public void testGCD() {
    assertEquals(BigInteger.valueOf(11), gcd(BigInteger.valueOf(99), BigInteger.valueOf(88)));
    assertEquals(BigInteger.valueOf(4), gcd(BigInteger.valueOf(100), BigInteger.valueOf(88)));
    assertEquals(BigInteger.valueOf(1), gcd(BigInteger.valueOf(101), BigInteger.valueOf(88)));

    BigInteger value1 = new BigInteger("595626405312658704599636370024200076395151472"
        + "771767924568367390440634854836130642401309734"
        + "848159964191109372749251136446006243551080802"
        + "443468883807974005482441860971736801068233313"
        + "491232693258531224835309787079942460705208862"
        + "391066304008929671240068786982214789313974208"
        + "121277040682624462127077310398113929298032224"
        + "406902296964052163261880140026245816176689134"
        + "688646742318394515117125117931094080634872026"
        + "347848034669856506748754843134161491874260587"
        + "327337252669362585036570429186753965648086618"
        + "124304059986689706447677381144991687868142047"
        + "249722766880033576608579416659574000278570884"
        + "27139264803160717153834950082732");

    BigInteger value2 = new BigInteger("274067527940324210164675059514169936706321077"
        + "716587152619149078627045197914608984656858592"
        + "528731102244773578922061217916250359858144388"
        + "719574574464967245379708325471583057239021064"
        + "917836916693411052993506893861086722124125827"
        + "022950820568155493697312539680639490524889344"
        + "865066516444160950915851524094176072393367374"
        + "025168676216783811304536549738909665484744275"
        + "067155246143772526555170109187191188801364722"
        + "517600818541591728004681673515982387104854566"
        + "903102995390579614393188326476025663247392444"
        + "533092715986735530343056344869496641278845202"
        + "223650494175070090294917949972774528610072824"
        + "458947092719245467211789956591268607216863753"
        + "679624901561412621785554722144008198982178053"
        + "538189569539219096856509995119366322932129988"
        + "343166711969023877595312100502075950468648415"
        + "477775557723829828636660991989941812527795172"
        + "124018492383737675186922273677672581350580918"
        + "910464808941060995674130486560435794939120577"
        + "191490033679039526235346391627269525486933484"
        + "148314307406131056278053429513527453093301308"
        + "815427956560369404097518372123409181494661668"
        + "490006843233849864232293233597067511416359584"
        + "421163109870277486363444655636394336745144869"
        + "526122913683198214580081892822347978030635397"
        + "012712786913863911810555531545723879718478274"
        + "2825997470004593284");

    BigInteger expected = new BigInteger("595626405312658704599636370024200076395151472"
        + "771767924568367390440634854836130642401309734"
        + "848159964191109372749251136446006243551080802"
        + "443468883807974005482441860971736801068233313"
        + "491232693258531224835309787079942460705208862"
        + "391066304008929671240068786982214789313974208"
        + "121277040682624462127077310398113929298032224"
        + "406902296964052163261880140026245816176689134"
        + "688646742318394515117125117931094080634872026"
        + "347848034669856506748754843134161491874260587"
        + "327337252669362585036570429186753965648086618"
        + "124304059986689706447677381144991687868142047"
        + "249722766880033576608579416659574000278570884"
        + "27139264803160717153834950082732");

    assertEquals(expected, gcd(value1, value2));
  }

  @Test
  public void testExactDivide() {
    assertEquals(BigInteger.valueOf(3), exactDivide(BigInteger.valueOf(9), BigInteger.valueOf(3)));
    assertEquals(BigInteger.valueOf(45),
        exactDivide(BigInteger.valueOf(405), BigInteger.valueOf(9)));

    BigInteger dividend = new BigInteger("149549685900357604711365590291031566035526303"
        + "666217655788425266893050220759490949381387996"
        + "113340249193205212006358207476102915864233508"
        + "445673545338384731376204260500544351862809578"
        + "500755834020230628011597390415539515481793796"
        + "638962442581762851010609120267994325937235024"
        + "122060565973719003459067958662103848086456328"
        + "063977055079883682984731020892264672735190066"
        + "669130984786795054476796294497031024898486036"
        + "710732125461890316563565246218457408640988616"
        + "615367786648614385837195199917422373519977639"
        + "493149820830723781919328856006299113582252867"
        + "798346016752082357503900767548084020257952243"
        + "154156196798902117127424860525511122797740083"
        + "070534988188448338790703722285678578027426765"
        + "138652344414275110060208957815614468475205876"
        + "118364251304654434878174828566610344451200790"
        + "176907159231573217298487079399565013742427206"
        + "472259433450544590033110420372032134485067236"
        + "689245111465434051861053684425921911955060621"
        + "372378266872130241270470990173442577938554446"
        + "319430544737301999604079684919684349888195990"
        + "204536014642166195524981565126633666739544322"
        + "538084213493811864768523477311312039937002783"
        + "195839979347648791524208564276124711227370690"
        + "889429028017588190348203601072752551186042564"
        + "415202061423454524191252818378350405950762293"
        + "552268847656702146395924232083613569453545534"
        + "600084534772708270103138492769705781269117862"
        + "466780014795721464622101596042968588402534529"
        + "373196463052834943966225739715019279806486615"
        + "840837534191652897120330332866884157496232878"
        + "782989444308600509438312498129849976294906408"
        + "323836726515538320460804319010363821185813574"
        + "477737631752075491076248044978437077271420823"
        + "703160956552283673224200974705830503486011689"
        + "138234510721039826372252236317519609263616036"
        + "420090453174837181322421646685106409320950056"
        + "459879348311906095052034436182361104449324851"
        + "534519570701900015060225580751481876645345011"
        + "581954713822612133312461036728578379305052159"
        + "004332379969309024");

    BigInteger divisor = new BigInteger("225603720638212426622364581197761418537222563"
        + "267033361785187037089204292821112238711850116"
        + "577810756801704734378359920747875827472208347"
        + "863340037167156203141861935405472157224736113"
        + "994227761093639341129764076519313123391552583"
        + "565764153510956202946721952971205980635376220"
        + "737334590482046384988331139983592370036202646"
        + "795735006122962398252553935888938251047945939"
        + "573053230232894572536369089776427276580934435"
        + "604760610009621111600264578461990254928703516"
        + "026166454337870147537284742526659070273224797"
        + "147819504376310789300244587561267973482441059"
        + "696682101159105035032984302334686128797424788"
        + "325204587923557915979737089591025556065666018"
        + "711367911724720035186584534763364625437251886"
        + "390385505390063174152420780400883518148237225"
        + "505627664287677445455272790910114685980349623"
        + "901814260256458020700699450455263465328935340"
        + "168831252713899303006897651360254817469903634"
        + "124009532257086748674752843530991929654572281"
        + "52926556699457079778712836442068");

    BigInteger expected = new BigInteger("662886611432183528949590270485248289731948598"
        + "106010990917709145898871301649058910325321993"
        + "493212278240250681970564349981097570411294981"
        + "796835805749094262962371915266482385921314837"
        + "943650257945495192717721963855657538947846327"
        + "518692062280631230177971103451198551828248730"
        + "205346102387056720017297331875934915728261740"
        + "890988432217158766976242168299271181854934458"
        + "294892510034496497778138104851045677887002183"
        + "652417165127676812704333998211404869027267109"
        + "872261146272830969430049271144211942125896897"
        + "680700603488623521012763811950474569425006246"
        + "978718302783637584005144490195611671418071877"
        + "469922995077663394721977781479941958997482919"
        + "802757252425524607138018910146230917903629648"
        + "128227618434531201814041991533407229323252677"
        + "937663048524870947415943422200752515374024181"
        + "198431755841218451467421607485099989421017949"
        + "545665747164922333056024261212092890039892582"
        + "978433508859370551356007591972970465620743683"
        + "1203984631159078153714736421368");

    assertEquals(expected, exactDivide(dividend, divisor));
  }

  @Test
  public void testKronecker() {
    // Prime (legendre)
    assertEquals(0, kronecker(BigInteger.valueOf(0), BigInteger.valueOf(7)));
    assertEquals(1, kronecker(BigInteger.valueOf(1), BigInteger.valueOf(7)));
    assertEquals(1, kronecker(BigInteger.valueOf(2), BigInteger.valueOf(7)));
    assertEquals(-1, kronecker(BigInteger.valueOf(3), BigInteger.valueOf(7)));
    assertEquals(1, kronecker(BigInteger.valueOf(4), BigInteger.valueOf(7)));
    assertEquals(-1, kronecker(BigInteger.valueOf(5), BigInteger.valueOf(7)));
    assertEquals(-1, kronecker(BigInteger.valueOf(6), BigInteger.valueOf(7)));
    assertEquals(0, kronecker(BigInteger.valueOf(7), BigInteger.valueOf(7)));

    // Non-prime odd (jacobi)
    assertEquals(0, kronecker(BigInteger.valueOf(0), BigInteger.valueOf(9)));
    assertEquals(1, kronecker(BigInteger.valueOf(1), BigInteger.valueOf(9)));
    assertEquals(1, kronecker(BigInteger.valueOf(2), BigInteger.valueOf(9)));
    assertEquals(0, kronecker(BigInteger.valueOf(3), BigInteger.valueOf(9)));
    assertEquals(1, kronecker(BigInteger.valueOf(4), BigInteger.valueOf(9)));
    assertEquals(1, kronecker(BigInteger.valueOf(5), BigInteger.valueOf(9)));
    assertEquals(0, kronecker(BigInteger.valueOf(6), BigInteger.valueOf(9)));
    assertEquals(1, kronecker(BigInteger.valueOf(7), BigInteger.valueOf(9)));
    assertEquals(1, kronecker(BigInteger.valueOf(8), BigInteger.valueOf(9)));
    assertEquals(0, kronecker(BigInteger.valueOf(9), BigInteger.valueOf(9)));

    // Anything (kronecker)
    assertEquals(0, kronecker(BigInteger.valueOf(0), BigInteger.valueOf(8)));
    assertEquals(1, kronecker(BigInteger.valueOf(1), BigInteger.valueOf(8)));
    assertEquals(0, kronecker(BigInteger.valueOf(2), BigInteger.valueOf(8)));
    assertEquals(-1, kronecker(BigInteger.valueOf(3), BigInteger.valueOf(8)));
    assertEquals(0, kronecker(BigInteger.valueOf(4), BigInteger.valueOf(8)));
    assertEquals(-1, kronecker(BigInteger.valueOf(5), BigInteger.valueOf(8)));
    assertEquals(0, kronecker(BigInteger.valueOf(6), BigInteger.valueOf(8)));
    assertEquals(1, kronecker(BigInteger.valueOf(7), BigInteger.valueOf(8)));
    assertEquals(0, kronecker(BigInteger.valueOf(8), BigInteger.valueOf(8)));

    assertEquals(0, kronecker(BigInteger.valueOf(0), BigInteger.valueOf(-8)));
    assertEquals(1, kronecker(BigInteger.valueOf(1), BigInteger.valueOf(-8)));
    assertEquals(0, kronecker(BigInteger.valueOf(2), BigInteger.valueOf(-8)));
    assertEquals(-1, kronecker(BigInteger.valueOf(3), BigInteger.valueOf(-8)));
    assertEquals(0, kronecker(BigInteger.valueOf(4), BigInteger.valueOf(-8)));
    assertEquals(-1, kronecker(BigInteger.valueOf(5), BigInteger.valueOf(-8)));
    assertEquals(0, kronecker(BigInteger.valueOf(6), BigInteger.valueOf(-8)));
    assertEquals(1, kronecker(BigInteger.valueOf(7), BigInteger.valueOf(-8)));
    assertEquals(0, kronecker(BigInteger.valueOf(8), BigInteger.valueOf(-8)));
  }

  private void testOddExamples() {
    // 2 ^ 3 = 8
    assertEquals(2, modPow(2, 3, 3));
    assertEquals(3, modPow(2, 3, 5));
    assertEquals(1, modPow(2, 3, 7));
    assertEquals(8, modPow(2, 3, 9));
  }

  private void testEvenExamples() {
    // 2 ^ 3 = 8
    assertEquals(0, modPow(2, 3, 2));
    assertEquals(0, modPow(2, 3, 4));
    assertEquals(2, modPow(2, 3, 6));
    assertEquals(0, modPow(2, 3, 8));
  }

  @Test public void testSignErrorsInsecure() {
    strategy = INSECURE;
    try {
      modPow(-1, 1, 1);
      fail("IllegalArgumentException expected");
    } catch (IllegalArgumentException expected) {
    }

    try {
      modPow(1, -1, 1);
      fail("IllegalArgumentException expected");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void testSignErrorsSecure() {
    strategy = SECURE;
    try {
      modPow(-1, 1, 1);
      fail("IllegalArgumentException expected");
    } catch (IllegalArgumentException expected) {
    }

    try {
      modPow(1, -1, 1);
      fail("IllegalArgumentException expected");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test public void testSmallExhaustiveInsecure() {
    for (int base = 10; base >= 0; --base) {
      for (int exp = 10; exp >= 0; --exp) {
        for (int mod = 10; mod >= -1; --mod) {
          this.strategy = JAVA;
          Object expected;
          try {
            expected = modPow(base, exp, mod);
          } catch (Exception e) {
            expected = e.getClass();
          }

          this.strategy = INSECURE;
          Object actual;
          try {
            actual = modPow(base, exp, mod);
          } catch (Exception e) {
            actual = e.getClass();
          }
          String message = String.format("base %d, exp %d, mod %d", base, exp, mod);
          assertEquals(message, expected, actual);
        }
      }
    }
  }

  @Test public void testSmallExhaustiveSecure() {
    for (int base = 10; base >= 0; --base) {
      for (int exp = 10; exp >= 0; --exp) {
        for (int mod = 10; mod >= -1; --mod) {
          this.strategy = JAVA;
          Object expected;
          try {
            expected = modPow(base, exp, mod);
          } catch (Exception e) {
            expected = e.getClass();
          }

          this.strategy = SECURE;
          Object actual;
          try {
            actual = modPow(base, exp, mod);
          } catch (Exception e) {
            actual = e.getClass();
          }
          if (mod > 0 && mod % 2 == 0) {
            // modPowSecure does not support even modulus
            assertEquals(IllegalArgumentException.class, actual);
          } else {
            String message = String.format("base %d, exp %d, mod %d", base, exp, mod);
            assertEquals(message, expected, actual);
          }
        }
      }
    }
  }

  @Test public void testVectorsJava() {
    strategy = JAVA;
    testVectors();
  }

  @Test public void testVectorInsecure() {
    strategy = INSECURE;
    testVectors();
  }

  @Test public void testVectorsSecure() {
    strategy = SECURE;
    testVectors();
  }

  private void testVectors() {
    doTest(ModPowVectors.VECTOR1);
    doTest(ModPowVectors.VECTOR2);
    doTest(ModPowVectors.VECTOR3);
  }

  private static void assertNotEquals(Object expected, Object actual) {
    if ((expected == null) != (actual == null)) {
      return;
    }
    if (expected != actual && !expected.equals(actual)) {
      return;
    }
    fail("Expected not equals, was: " + actual);
  }
}
