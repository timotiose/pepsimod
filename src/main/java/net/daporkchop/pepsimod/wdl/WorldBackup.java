/*
 * Adapted from the Wizardry License
 *
 * Copyright (c) 2017 Team Pepsi
 *
 * Permission is hereby granted to any persons and/or organizations using this software to copy, modify, merge, publish, and distribute it.
 * Said persons and/or organizations are not allowed to use the software or any derivatives of the work for commercial use or any other means to generate income, nor are they allowed to claim this software as their own.
 *
 * The persons and/or organizations are also disallowed from sub-licensing and/or trademarking this software without explicit permission from Team Pepsi.
 *
 * Any persons and/or organizations using this software must disclose their source code and have it publicly available, include this license, provide sufficient credit to the original authors of the project (IE: Team Pepsi), as well as provide a link to the original project.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.daporkchop.pepsimod.wdl;

import net.minecraft.client.resources.I18n;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Performs backup of worlds.
 */
public class WorldBackup {
    /**
     * The format that is used for world date saving.
     * <p>
     * TODO: Allow modification ingame?
     */
    private static final DateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    private WorldBackup() {
    }

    /**
     * Backs up the given world with the selected type.
     *
     * @param worldFolder The folder that contains the world to backup.
     * @param worldName   The name of the world.
     * @param type        The type to backup with.
     * @param monitor     A monitor.
     * @throws IOException
     */
    public static void backupWorld(File worldFolder, String worldName,
                                   WorldBackupType type, IBackupProgressMonitor monitor) throws IOException {
        String newWorldName = worldName + "_" + DATE_FORMAT.format(new Date());

        switch (type) {
            case NONE: {
                return;
            }
            case FOLDER: {
                File destination = new File(worldFolder.getParentFile(),
                        newWorldName);

                if (destination.exists()) {
                    throw new IOException("Backup folder (" + destination +
                            ") already exists!");
                }

                copyDirectory(worldFolder, destination, monitor);
                return;
            }
            case ZIP: {
                File destination = new File(worldFolder.getParentFile(),
                        newWorldName + ".zip");

                if (destination.exists()) {
                    throw new IOException("Backup file (" + destination +
                            ") already exists!");
                }

                zipDirectory(worldFolder, destination, monitor);
                return;
            }
        }
    }

    /**
     * Copies a directory.
     */
    public static void copyDirectory(File src, File destination,
                                     IBackupProgressMonitor monitor) throws IOException {
        monitor.setNumberOfFiles(countFilesInFolder(src));

        copy(src, destination, src.getPath().length() + 1, monitor);
    }

    /**
     * Zips a directory.
     */
    public static void zipDirectory(File src, File destination,
                                    IBackupProgressMonitor monitor) throws IOException {
        monitor.setNumberOfFiles(countFilesInFolder(src));

        try (FileOutputStream outStream = new FileOutputStream(destination)) {
            try (ZipOutputStream stream = new ZipOutputStream(outStream)) {
                zipFolder(src, stream, src.getPath().length() + 1, monitor);
            }
        }
    }

    /**
     * Recursively adds a folder to a zip stream.
     *
     * @param folder         The folder to zip.
     * @param stream         The stream to write to.
     * @param pathStartIndex The start of the file path.
     * @param monitor        A monitor.
     * @throws IOException
     */
    private static void zipFolder(File folder, ZipOutputStream stream,
                                  int pathStartIndex, IBackupProgressMonitor monitor) throws IOException {
        for (File file : folder.listFiles()) {
            if (file.isFile()) {
                String name = file.getPath().substring(pathStartIndex);
                monitor.onNextFile(name);
                ZipEntry zipEntry = new ZipEntry(name);
                stream.putNextEntry(zipEntry);
                try (FileInputStream inputStream = new FileInputStream(file)) {
                    IOUtils.copy(inputStream, stream);
                }
                stream.closeEntry();
            } else if (file.isDirectory()) {
                zipFolder(file, stream, pathStartIndex, monitor);
            }
        }
    }

    /**
     * Copies a series of files from one folder to another.
     *
     * @param from           The file to copy.
     * @param to             The new location for the file.
     * @param pathStartIndex The start of the file path.
     * @param monitor        A monitor.
     * @throws IOException
     */
    private static void copy(File from, File to, int pathStartIndex,
                             IBackupProgressMonitor monitor) throws IOException {
        if (from.isDirectory()) {
            if (!to.exists()) {
                to.mkdir();
            }

            for (String fileName : from.list()) {
                copy(new File(from, fileName),
                        new File(to, fileName), pathStartIndex, monitor);
            }
        } else {
            monitor.onNextFile(to.getPath().substring(pathStartIndex));
            //Yes, FileUtils#copyDirectory exists, but we can't monitor the
            //progress using it.
            FileUtils.copyFile(from, to, true);
        }
    }

    /**
     * Recursively counts the number of files in the given folder.
     * Directories are not included in this count, but the files
     * contained within are.
     *
     * @param folder
     */
    private static int countFilesInFolder(File folder) {
        if (!folder.isDirectory()) {
            return 0;
        }

        int count = 0;
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                count += countFilesInFolder(file);
            } else {
                count++;
            }
        }

        return count;
    }

    /**
     * Different modes for backups.
     */
    public enum WorldBackupType {
        /**
         * No backup is performed.
         */
        NONE("net.daporkchop.pepsimod.wdl.backup.none", ""),
        /**
         * The world folder is copied.
         */
        FOLDER("net.daporkchop.pepsimod.wdl.backup.folder", "net.daporkchop.pepsimod.wdl.saveProgress.backingUp.title.folder"),
        /**
         * The world folder is copied to a zip folder.
         */
        ZIP("net.daporkchop.pepsimod.wdl.backup.zip", "net.daporkchop.pepsimod.wdl.saveProgress.backingUp.title.zip");

        /**
         * I18n key for the description (used on buttons).
         */
        public final String descriptionKey;
        /**
         * I18n key for the backup screen title.
         */
        public final String titleKey;

        WorldBackupType(String descriptionKey, String titleKey) {
            this.descriptionKey = descriptionKey;
            this.titleKey = titleKey;
        }

        /**
         * Attempts to parse the given string into a WorldBackupType.  This
         * is performed case-insensitively.
         *
         * @param name The name of the backup type.
         * @return The backup type corresponding to the name.
         */
        public static WorldBackupType match(String name) {
            for (WorldBackupType type : WorldBackupType.values()) {
                if (type.name().equalsIgnoreCase(name)) {
                    return type;
                }
            }

            return WorldBackupType.NONE;
        }

        /**
         * Gets the (translated) description for this backup type.
         *
         * @return
         */
        public String getDescription() {
            return I18n.format(descriptionKey);
        }

        /**
         * Gets the (translated) title for this backup type.
         *
         * @return
         */
        public String getTitle() {
            return I18n.format(titleKey);
        }
    }

    /**
     * Something that listens to backup progress.
     */
    public interface IBackupProgressMonitor {
        /**
         * Sets the initial number of files.
         */
        void setNumberOfFiles(int num);

        /**
         * Called on the next file.
         *
         * @param name Name of the new file.
         */
        void onNextFile(String name);
    }
}
