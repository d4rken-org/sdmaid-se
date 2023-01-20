//package eu.darken.sdmse.appcleaner.core.forensics;
//
//import com.squareup.moshi.Json;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collection;
//import java.util.List;
//import java.util.regex.Pattern;
//
//import androidx.annotation.Keep;
//import androidx.annotation.Nullable;
//import eu.thedarken.sdm.tools.forensics.Location;
//
///**
// * App File Filter
// */
//@Keep
//public class AppFilter {
//    @Json(name = "packages") private final List<String> packageNames;
//    @Json(name = "fileFilter") private final List<FileFilter> fileFilterList;
//
//    public AppFilter() {
//        packageNames = new ArrayList<>();
//        fileFilterList = new ArrayList<>();
//    }
//
//    public AppFilter(List<String> packageNames, List<FileFilter> fileFilterList) {
//        this.packageNames = packageNames;
//        this.fileFilterList = fileFilterList;
//    }
//
//    public List<String> getPackageNames() {
//        return packageNames;
//    }
//
//    public List<FileFilter> getFileFilters() {
//        return fileFilterList;
//    }
//
//    public boolean matches(String packageName, String prefixFreePath, Location location) {
//        if (!packageNames.isEmpty() && !packageNames.contains(packageName)) return false;
//        for (FileFilter filter : fileFilterList) if (filter.matches(prefixFreePath, location)) return true;
//        return false;
//    }
//
//    @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//
//        AppFilter appFilter = (AppFilter) o;
//
//        return packageNames.equals(appFilter.packageNames) && fileFilterList.equals(appFilter.fileFilterList);
//    }
//
//    @Override
//    public int hashCode() {
//        int result = packageNames.hashCode();
//        result = 31 * result + fileFilterList.hashCode();
//        return result;
//    }
//
//    public static Builder forApps(String... packageNames) {
//        return new Builder(Arrays.asList(packageNames));
//    }
//
//    public static class Builder {
//        final List<String> packageNames = new ArrayList<>();
//        final List<FileFilter> fileFilterList = new ArrayList<>();
//
//        Builder(@Nullable Collection<String> packageNames) {
//            if (packageNames != null) this.packageNames.addAll(packageNames);
//        }
//
//        public Locations filter() {
//            final List<Location> locationsResult = new ArrayList<>();
//            final List<String> startsWithResult = new ArrayList<>();
//            final List<String> containsResult = new ArrayList<>();
//            final List<Pattern> patternResult = new ArrayList<>();
//            final List<String> notContainsResult = new ArrayList<>();
//
//            final Build buildCallback = new Build() {
//                private FileFilter buildFileFilter() {
//                    return new FileFilter(locationsResult, startsWithResult, containsResult, notContainsResult, patternResult);
//                }
//
//
//                @Override
//                public AppFilter build() {
//                    fileFilterList.add(buildFileFilter());
//                    return Builder.this.build();
//                }
//
//
//                @Override
//                public Locations and() {
//                    fileFilterList.add(buildFileFilter());
//                    return Builder.this.filter();
//                }
//
//                @Override
//                public void addTo(Collection<AppFilter> appFilters) {
//                    fileFilterList.add(buildFileFilter());
//                    appFilters.add(Builder.this.build());
//                }
//            };
//
//            final Negatives negativesCallback = new Negatives() {
//
//                @Override
//                public Build notContains(String... doesNotContain) {
//                    notContainsResult.addAll(Arrays.asList(doesNotContain));
//                    return buildCallback;
//                }
//
//
//                @Override
//                public AppFilter build() {
//                    return buildCallback.build();
//                }
//
//
//                @Override
//                public Locations and() {
//                    return buildCallback.and();
//                }
//
//                @Override
//                public void addTo(Collection<AppFilter> appFilters) {
//                    buildCallback.addTo(appFilters);
//                }
//            };
//
//            final Positives positivesCall = regexes -> {
//                for (String regex : regexes) {
//                    patternResult.add(Pattern.compile(regex));
//                }
//                return negativesCallback;
//            };
//            final Precursors precursorsCall = new Precursors() {
//                @Override
//                public Positives contains(String... contains) {
//                    containsResult.addAll(Arrays.asList(contains));
//                    return positivesCall;
//                }
//
//                @Override
//                public Positives startsWith(String... startsWith) {
//                    startsWithResult.addAll(Arrays.asList(startsWith));
//                    return positivesCall;
//                }
//            };
//
//            return new Locations() {
//
//                @Override
//                public Precursors at(Location... locations) {
//                    locationsResult.addAll(Arrays.asList(locations));
//                    return precursorsCall;
//                }
//
//
//                @Override
//                public Precursors atAllLocations() {
//                    return precursorsCall;
//                }
//            };
//        }
//
//        public AppFilter build() {
//            if (fileFilterList.isEmpty()) {
//                throw new IllegalArgumentException("AooFilter contains no pathes or patterns.");
//            }
//            return new AppFilter(packageNames, fileFilterList);
//        }
//
//        public void addTo(Collection<AppFilter> collection) {
//            collection.add(build());
//        }
//
//        public interface Build {
//            AppFilter build();
//
//            Locations and();
//
//            void addTo(Collection<AppFilter> appFilters);
//        }
//
//        public interface Negatives extends Build {
//            Build notContains(String... doesNotContain);
//        }
//
//        public interface Positives {
//            Negatives matches(String... regexes);
//        }
//
//        public interface Precursors {
//            Positives contains(String... contains);
//
//            Positives startsWith(String... startsWith);
//        }
//
//        public interface Locations {
//            Precursors at(Location... locations);
//
//            Precursors atAllLocations();
//        }
//    }
//}
