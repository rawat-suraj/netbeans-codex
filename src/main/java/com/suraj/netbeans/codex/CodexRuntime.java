/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.suraj.netbeans.codex;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 *
 * @author suraj
 */
public class CodexRuntime {
    private static final String RESOURCE = "/codex-runtime/codex-windows-x64.zip";

    private CodexRuntime(){}

    public static synchronized Path prepare() throws IOException {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch");

        if(!os.startsWith("windows") || !(arch.equals("amd64") || arch.equals("x86_64"))){
            throw new IOException("This runtime supports windows x64");
        }

        String archiveHash = calculateHash();
        String localAppData = System.getenv("LOCALAPPDATA");

        Path base = localAppData != null ? Path.of(localAppData) : Path.of(System.getProperty("user.home"), "AppData", "Local");

        Path directory = base.resolve("NetBeansCodex").resolve("runtime").resolve(archiveHash);

        Path executable = directory.resolve("codex-x86_64-pc-windows-msvc.exe");
        Path completed = directory.resolve(".extracted");

        if(Files.isRegularFile(completed) && Files.isRegularFile(executable)){
            return executable;
        }

        Files.createDirectories(directory);

        try(ZipInputStream zip = new ZipInputStream(openArchive())) {
            ZipEntry entry;

            while((entry = zip.getNextEntry()) != null){
                Path destination = directory.resolve(entry.getName()).normalize();

                // Prevent zip entries from writing outside this directory
                if(!destination.startsWith(directory)){
                    throw new IOException("Invalid ZIP entry: "+entry.getName());
                }

                if(entry.isDirectory()){
                    Files.createDirectories(destination);
                }else{
                    Files.createDirectories(destination.getParent());
                    Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
                }

                zip.closeEntry();
            }
        }

        if(!Files.isRegularFile(executable)){
            throw new IOException("Codex executable was not found at the ZIP root.");
        }

        Files.writeString(completed, archiveHash);

        return executable;
    }

    private static InputStream openArchive() throws IOException {
        InputStream stream = CodexRuntime.class.getResourceAsStream(RESOURCE);

        if(stream == null){
            throw new IOException("Bundled zip not found: " + RESOURCE);
        }

        return stream;
    }

    private static String calculateHash() throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try(DigestInputStream stream = new DigestInputStream(openArchive(), digest)){
                byte[] buffer = new byte[8192];
                while(stream.read(buffer) != -1){
                    // DigestInputStream updates the hash as it reads
                }
            }

            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
