//package eu.darken.sdmse.appcleaner.core.forensics;
//
//import org.junit.Rule;
//import org.junit.Test;
//import org.mockito.junit.MockitoJUnit;
//import org.mockito.junit.MockitoRule;
//
//import java.io.FileFilter;
//import java.util.Collections;
//import java.util.regex.Pattern;
//
//import eu.thedarken.sdm.tools.forensics.Location;
//
//import static org.hamcrest.core.Is.is;
//import static org.hamcrest.core.IsNull.notNullValue;
//import static org.junit.Assert.assertThat;
//
//public class FileFilterTest {
//    @Rule public MockitoRule rule = MockitoJUnit.rule();
//
//    @Test
//    public void testInit() {
//        FileFilter fileFilter = new FileFilter(
//                Collections.emptyList(),
//                Collections.emptyList(),
//                Collections.emptyList(),
//                Collections.emptyList(),
//                Collections.emptyList());
//        assertThat(fileFilter, is(notNullValue()));
//    }
//
//    @Test
//    public void testLocation() {
//        FileFilter fileFilter = new FileFilter(
//                Collections.singletonList(Location.SDCARD),
//                Collections.singletonList("con"),
//                Collections.singletonList("contains"),
//                Collections.emptyList(),
//                Collections.singletonList(Pattern.compile("contains")));
//
//        assertThat(fileFilter.matches("contains", Location.SDCARD), is(true));
//        assertThat(fileFilter.matches("contains", Location.PUBLIC_DATA), is(false));
//    }
//
//    @Test
//    public void testBadMatch() {
//        FileFilter fileFilter = new FileFilter(
//                Collections.emptyList(),
//                Collections.singletonList("con"),
//                Collections.singletonList("contains"),
//                Collections.singletonList("contains"),
//                Collections.singletonList(Pattern.compile("contains")));
//
//        assertThat(fileFilter.matches("contains", Location.SDCARD), is(false));
//    }
//
//    @Test
//    public void testCaseSensitivity() {
//        FileFilter fileFilter = new FileFilter(
//                Collections.emptyList(),
//                Collections.singletonList("con"),
//                Collections.singletonList("contains"),
//                Collections.singletonList("nope"),
//                Collections.singletonList(Pattern.compile("contains")));
//
//        assertThat(fileFilter.matches("contains", Location.SDCARD), is(true));
//        assertThat(fileFilter.matches("CONTAINS", Location.SDCARD), is(true));
//
//        assertThat(fileFilter.matches("contains", Location.PRIVATE_DATA), is(true));
//        assertThat(fileFilter.matches("CONTAINS", Location.PRIVATE_DATA), is(false));
//    }
//
//    @Test
//    public void testStartsWith() {
//        FileFilter fileFilter = new FileFilter(
//                Collections.emptyList(),
//                Collections.singletonList("abcd"),
//                Collections.emptyList(),
//                Collections.emptyList(),
//                Collections.singletonList(Pattern.compile(".+")));
//
//        assertThat(fileFilter.matches("abcdefg", Location.SDCARD), is(true));
//        assertThat(fileFilter.matches("bcdefg", Location.SDCARD), is(false));
//    }
//
//    @Test
//    public void testContains() {
//        FileFilter fileFilter = new FileFilter(
//                Collections.emptyList(),
//                Collections.emptyList(),
//                Collections.singletonList("bcdefg"),
//                Collections.emptyList(),
//                Collections.singletonList(Pattern.compile(".+")));
//
//        assertThat(fileFilter.matches("abcdefg", Location.SDCARD), is(true));
//        assertThat(fileFilter.matches("abcdef", Location.SDCARD), is(false));
//    }
//}
