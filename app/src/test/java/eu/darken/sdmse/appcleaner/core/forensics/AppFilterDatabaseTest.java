//package eu.darken.sdmse.appcleaner.core.forensics;
//
//import org.junit.Rule;
//import org.junit.Test;
//import org.mockito.junit.MockitoJUnit;
//import org.mockito.junit.MockitoRule;
//
//import java.io.ByteArrayInputStream;
//import java.io.FileFilter;
//import java.util.UUID;
//import java.util.regex.Pattern;
//
//import eu.thedarken.sdm.tools.forensics.Location;
//
//import static junit.framework.Assert.assertFalse;
//import static junit.framework.Assert.assertNotSame;
//import static junit.framework.Assert.assertTrue;
//
//public class AppFilterDatabaseTest {
//    @Rule public MockitoRule rule = MockitoJUnit.rule();
//    private final static String JSONDATA = "{\n" +
//            "  \"schemaVersion\": 1,\n" +
//            "  \"appFilter\": [\n" +
//            "    {\n" +
//            "      \"packages\": [\"com.whatsapp\", \"com.whatsapp.pro\"],\n" +
//            "      \"fileFilter\": [\n" +
//            "        {\n" +
//            "          \"locations\": [\"SDCARD\"],\n" +
//            "          \"contains\": [\"WhatsApp/Media/WhatsApp\"],\n" +
//            "          \"notContains\": [\".nomedia\"],\n" +
//            "          \"patterns\": [\n" +
//            "            \"(?>WhatsApp/Media/WhatsApp Video/Sent/)(?:[\\\\W\\\\w]+?)$\"\n" +
//            "          ]\n" +
//            "        }, {\n" +
//            "          \"contains\": [\"Something/Media/WhatsApp\", \"PlaceHolder/Media\"],\n" +
//            "          \"patterns\": [\n" +
//            "            \"(?>Something/Media/WhatsApp Video/Sent/)(?:[\\\\W\\\\w]+?)$\",\n" +
//            "            \"(?>PlaceHolder/Media/WhatsApp Images/Sent/)(?:[\\\\W\\\\w]+?)$\"\n" +
//            "          ]\n" +
//            "        }\n" +
//            "      ]\n" +
//            "    }\n" +
//            "  ]\n" +
//            "}";
//
//    private static final AppFilter TESTFILTER = AppFilter.forApps("com.whatsapp", "com.whatsapp.pro").
//            filter().at(Location.SDCARD)
//            .contains("WhatsApp/Media/WhatsApp")
//            .matches("(?>WhatsApp/Media/WhatsApp Video/Sent/)(?:[\\W\\w]+?)$")
//            .notContains(".nomedia").and()
//            .atAllLocations()
//            .contains("Something/Media/WhatsApp", "PlaceHolder/Media")
//            .matches("(?>Something/Media/WhatsApp Video/Sent/)(?:[\\W\\w]+?)$",
//                    "(?>PlaceHolder/Media/WhatsApp Images/Sent/)(?:[\\W\\w]+?)$")
//            .build();
//
//    @Test
//    public void testJsonParsing() throws Exception {
//        AppFilterDatabase database = AppFilterDatabase.fromInputStream(new ByteArrayInputStream(JSONDATA.getBytes()));
//        assertEquals(1, database.getSchemaVersion());
//        assertEquals(1, database.getAppFilter().size());
//        assertEquals(TESTFILTER, database.getAppFilter().get(0));
//    }
//
//    @Test
//    public void testAppFilterCreation() {
//        assertEquals("com.whatsapp", TESTFILTER.getPackageNames().get(0));
//        assertEquals("com.whatsapp.pro", TESTFILTER.getPackageNames().get(1));
//        assertEquals(2, TESTFILTER.getPackageNames().size());
//
//        assertEquals(2, TESTFILTER.getFileFilters().size());
//
//        FileFilter first = TESTFILTER.getFileFilters().get(0);
//        assertEquals(Location.SDCARD, first.getLocations().get(0));
//        assertEquals(1, first.getLocations().size());
//
//        assertEquals("WhatsApp/Media/WhatsApp", first.getContains().get(0));
//        assertEquals(1, first.getContains().size());
//
//        assertEquals(".nomedia", first.getNotContains().get(0));
//        assertEquals(1, first.getNotContains().size());
//
//        assertEquals("(?>WhatsApp/Media/WhatsApp Video/Sent/)(?:[\\W\\w]+?)$", first.getPatterns().get(0).pattern());
//        assertEquals(1, first.getPatterns().size());
//
//        FileFilter second = TESTFILTER.getFileFilters().get(1);
//
//        assertEquals("Something/Media/WhatsApp", second.getContains().get(0));
//        assertEquals("PlaceHolder/Media", second.getContains().get(1));
//        assertEquals(2, second.getContains().size());
//
//        assertEquals("(?>Something/Media/WhatsApp Video/Sent/)(?:[\\W\\w]+?)$", second.getPatterns().get(0).pattern());
//        assertEquals("(?>PlaceHolder/Media/WhatsApp Images/Sent/)(?:[\\W\\w]+?)$", second.getPatterns().get(1).pattern());
//        assertEquals(2, second.getPatterns().size());
//    }
//
//    @Test
//    public void testAppFilterHashMapAndEquals() throws Exception {
//        AppFilterDatabase database = AppFilterDatabase.fromInputStream(new ByteArrayInputStream(JSONDATA.getBytes()));
//        assertEquals(1, database.getSchemaVersion());
//        assertEquals(1, database.getAppFilter().size());
//        assertEquals(TESTFILTER, database.getAppFilter().get(0));
//
//        database.getAppFilter().get(0).getFileFilters().get(0).getPatterns().add(Pattern.compile("test"));
//        assertNotSame(TESTFILTER, database.getAppFilter().get(0));
//    }
//
//    @Test
//    public void testAppFilterMatch() throws Exception {
//        AppFilterDatabase database = AppFilterDatabase.fromInputStream(new ByteArrayInputStream(JSONDATA.getBytes()));
//        for (AppFilter appFilter : database.getAppFilter()) {
//            assertTrue(appFilter.matches("com.whatsapp", "WhatsApp/Media/WhatsApp Video/Sent/" + UUID.randomUUID().toString(), Location.SDCARD));
//            assertTrue(appFilter.matches("com.whatsapp.pro", "WhatsApp/Media/WhatsApp Video/Sent/" + UUID.randomUUID().toString(), Location.SDCARD));
//            assertFalse(appFilter.matches("com.whatsapp", "WhatsApp/Media/WhatsApp Video/Sent/" + UUID.randomUUID().toString(), Location.PRIVATE_DATA));
//            assertTrue(appFilter.matches("com.whatsapp", "Something/Media/WhatsApp Video/Sent/" + UUID.randomUUID().toString(), Location.SYSTEM));
//            assertTrue(appFilter.matches("com.whatsapp", "PlaceHolder/Media/WhatsApp Images/Sent/" + UUID.randomUUID().toString(), Location.SYSTEM));
//        }
//    }
//
//    @Test
//    public void testAppFilterMatch_case_sensitivity() throws Exception {
//        AppFilterDatabase database = AppFilterDatabase.fromInputStream(new ByteArrayInputStream(JSONDATA.getBytes()));
//        for (AppFilter appFilter : database.getAppFilter()) {
//            assertTrue(appFilter.matches("com.whatsapp", "WhatsApp/Media/WhatsApp Video/Sent/Test123", Location.SDCARD));
//            assertTrue(appFilter.matches("com.whatsapp", "whatsApp/media/whatsapp video/sent/test123", Location.SDCARD));
//
//            assertTrue(appFilter.matches("com.whatsapp", "something/media/whatsapp video/sent/test123", Location.SDCARD));
//            assertTrue(appFilter.matches("com.whatsapp", "Something/Media/WhatsApp Video/Sent/Test123", Location.SDCARD));
//
//            assertFalse(appFilter.matches("com.whatsapp", "something/media/whatsapp video/sent/test123", Location.PRIVATE_DATA));
//            assertTrue(appFilter.matches("com.whatsapp", "Something/Media/WhatsApp Video/Sent/Test123", Location.PRIVATE_DATA));
//
//            assertTrue(appFilter.matches("com.whatsapp", "PlaceHolder/Media/WhatsApp Images/Sent/Test123", Location.SYSTEM));
//            assertFalse(appFilter.matches("com.whatsapp", "placeHolder/media/whatsapp video/sent/test123", Location.SYSTEM));
//        }
//    }
//}
