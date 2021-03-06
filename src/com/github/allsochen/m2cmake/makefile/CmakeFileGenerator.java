package com.github.allsochen.m2cmake.makefile;

import com.github.allsochen.m2cmake.configuration.JsonConfig;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;

public class CmakeFileGenerator {
    private String app;
    private String target;
    private String basePath;
    private TafMakefileProperty tafMakefileProperty;
    private JsonConfig jsonConfig;

    public CmakeFileGenerator(String app, String target, String basePath, TafMakefileProperty tafMakefileProperty,
                              JsonConfig jsonConfig) {
        this.app = app;
        this.target = target;
        this.basePath = basePath;
        this.tafMakefileProperty = tafMakefileProperty;
        this.jsonConfig = jsonConfig;
    }

    /**
     * Filter the ../.. path and transfer to the real path.
     *
     * @param includePath
     * @return
     */
    private String transferIncludePath(String includePath) {
        if (!includePath.matches(".*[a-zA-z].*")) {
            return "";
        }
        return transferMapping(includePath);
    }

    /**
     * Remove the `include` and `/xxx.mk` fragment.
     * include /home/tafjce/MTT/AServer/AServer.mk => /home/tafjce/MTT/AServer
     *
     * @param jceIncludePath
     * @return
     */
    private String transferJceIncludePath(String jceIncludePath) {
        String newPath = convertToRealFilePath(jceIncludePath);
        newPath = newPath.substring(0, newPath.lastIndexOf("/"));
        return newPath;
    }

    /**
     * Remove the `include` prefix.
     * include /home/tafjce/MTT/AServer/Aserver.mk => /home/tafjce/MTT/AServer/AServer.mk
     *
     * @param includePath
     * @return
     */
    private String convertToRealFilePath(String includePath) {
        String newPath = transferMapping(includePath);
        newPath = newPath.replace("include ", "").trim();
        return newPath;
    }

    private String transferMapping(String path) {
        Map<String, String> dirMappings = jsonConfig.getDirMappings();
        Iterator<Map.Entry<String, String>> iterator = dirMappings.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            if (path.contains(entry.getKey())) {
                return path.replace(entry.getKey(), entry.getValue());
            }
        }
        return path;
    }

    public static File getCmakeListFile(String basePath) {
        return new File(basePath + File.separator + "CMakeLists.txt");
    }

    public void create() throws IOException {
        File cmakeFile = getCmakeListFile(this.basePath);
        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(cmakeFile), "UTF-8"));
        // Write header.
        bw.write("# This file is generated by TAF m2cmake plugin\n");
        bw.write("# http://www.github.com/allsochen/clion-m2cmake\n");
        bw.newLine();
        String cmakeVersion = "3.10";
        if (!jsonConfig.getCmakeVersion().isEmpty()) {
            cmakeVersion = jsonConfig.getCmakeVersion();
        }
        bw.write("cmake_minimum_required(VERSION " + cmakeVersion + ")");
        bw.newLine();

        bw.write("project(" + target + ")");
        bw.newLine();
        bw.write("set(CMAKE_CXX_STANDARD 11)");
        bw.newLine();

        String cxxFlags = "-std=c++11 -Wno-narrowing -fno-strict-aliasing -Wno-deprecated-declarations -fPIC -Wno-deprecated -Wall";
        bw.write("set(CMAKE_CXX_FLAGS \"" + cxxFlags + "\")");
        bw.newLine();

        bw.newLine();
        bw.write("#配置include");
        bw.newLine();

        Set<String> configIncludes = new LinkedHashSet<>(this.jsonConfig.getIncludes());
        if (configIncludes != null) {
            for (String include : configIncludes) {
                bw.write("include_directories(" + include + ")");
                bw.newLine();
            }
        }

        bw.newLine();
        bw.write("#服务include");
        bw.newLine();
        Set<String> includes = new LinkedHashSet<>(this.tafMakefileProperty.getIncludes());
        for (String include : includes) {
            include = transferIncludePath(include);
            if (include != null && !include.isEmpty()) {
                bw.write("include_directories(" + transferIncludePath(include) + ")");
                bw.newLine();
            }
        }
        bw.newLine();

        bw.write("#服务jce目录");
        bw.newLine();
        String currentServerJcePath = "include /home/tafjce/" + app + "/" + target + "/" + target + ".mk";
        bw.write("include_directories(" + transferJceIncludePath(currentServerJcePath) + ")");
        bw.newLine();
        bw.newLine();

        bw.write("#服务依赖jce");
        bw.newLine();

        Set<String> allJceRealIncludes = new LinkedHashSet<>();
        List<String> jceIncludes = this.tafMakefileProperty.getJceIncludes();

        for (String jceInclude : jceIncludes) {
            // Try to analysis from the real mk file.
            // Only analysis the first floor to void death circle.
            File file = new File(convertToRealFilePath(jceInclude));
            if (file.exists()) {
                TafMakefileProperty tafMakefileProperty = TafMakefileAnalysis.extractInclude(file);
                tafMakefileProperty.getIncludes().forEach(referenceInclude -> {
                    referenceInclude = transferIncludePath(referenceInclude);
                    if (referenceInclude != null && !referenceInclude.isEmpty()) {
                        allJceRealIncludes.add(referenceInclude);
                    }
                });
                tafMakefileProperty.getJceIncludes().forEach(referenceJceInclude -> {
                    referenceJceInclude = transferJceIncludePath(referenceJceInclude);
                    if (referenceJceInclude != null && !referenceJceInclude.isEmpty()) {
                        allJceRealIncludes.add(referenceJceInclude);
                    }
                });
            } else {
                allJceRealIncludes.add(transferJceIncludePath(jceInclude));
            }
        }

        List<String> sortedRealIncludes = new ArrayList<>(allJceRealIncludes);
        Collections.sort(sortedRealIncludes);
        for (String include : sortedRealIncludes) {
            if (include != null && !include.isEmpty()) {
                bw.write("include_directories(" + include + ")");
                bw.newLine();
            }
        }
        bw.newLine();

        bw.write("file(GLOB_RECURSE CMAKE_FILES *.cpp *.h)");
        bw.newLine();
        bw.write("include_directories(./)");
        bw.newLine();
        bw.write("add_executable(" + target + " ${CMAKE_FILES})");
        bw.newLine();
        bw.flush();
        bw.close();
        cmakeFile.setLastModified(System.currentTimeMillis());
    }
}
