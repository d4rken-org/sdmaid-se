# Play scores obfuscation as part of app optimization (threshold 25%), so this flavor is
# obfuscated. Keep file/line attributes so R8 retrace with the mapping file recovers the
# original frames from Play vitals and user-submitted logs.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
