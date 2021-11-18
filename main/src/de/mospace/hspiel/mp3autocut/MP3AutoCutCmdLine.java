package de.mospace.hspiel.mp3autocut;

/* Copyright (C) 2010 Moritz Ringler
 * $Id: MP3AutoCut.java 15 2010-01-05 16:05:01Z ringler $
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
 
 
import java.io.File;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.IOException;

class MP3AutoCutCmdLine{
    private boolean writeMPD = false;
    private boolean writeMP3 = false;
    private File targetdir = null;
    private File[] mp3s = new File[0];

    private static final String NEA = "Not enough arguments";
    private static final String WRITE_MPD = "mpd";
    private static final String WRITE_MP3 = "mp3";


    public MP3AutoCutCmdLine(String[] argv) throws Exception{
            int k = 0;
            final int nargs = argv.length;

            if(nargs == k){
                throw new IllegalArgumentException(NEA);
            }

            final String command = argv[k++];
            if (WRITE_MPD.equals(command)){
                writeMPD = true;
            } else if (WRITE_MP3.equals(command)){
                writeMP3 = true;
                if(nargs == k){
                    throw new IllegalArgumentException(NEA);
                }
                targetdir = new File(argv[k++]);
                if (!targetdir.isDirectory()){
                    throw new IllegalArgumentException(targetdir.toString() + " is not a directory.");
                }
            } else {
                throw new IllegalArgumentException("First argument must be one of " +
                    java.util.Arrays.asList(new String[]{ WRITE_MPD, WRITE_MP3 } ));
            }

            if(nargs == k){
                throw new IllegalArgumentException(NEA);
            }
            mp3s = new File[nargs - k];
            for (int j = 0; k < nargs; k++){
                mp3s[j] = new File(argv[k]);
                if (!mp3s[j++].isFile()){
                    throw new java.io.FileNotFoundException(argv[k] + " is not a file.");
                }
            }
    }

    public boolean getWriteMPD(){
        return writeMPD;
    }

    public boolean getWriteMP3(){
        return writeMP3;
    }

    public File getOutputDir(){
        return targetdir;
    }

    public File[] getMP3s(){
        return mp3s;
    }

    public static void printGPL(){
        try{
            cat("/help/gpl3-short.txt", MP3AutoCut.class);
        } catch (Exception ex){
            throw new Error(ex);
        }
    }

    public static void printHelp(){
        try{
            cat("/help/mp3autocut.txt", MP3AutoCut.class);
        } catch (Exception ex){
            throw new Error(ex);
        }
    }

    public static String getUsage(){
        final StringBuilder sb = new StringBuilder();
        sb.append("USAGE:\n");
        sb.append("\tjava -jar mp3autocut.jar mpd MP3FILE1 [MP3FILE2 ...]\n");
        sb.append("OR\n");
        sb.append("\tjava -jar mp3autocut.jar mp3 OUTPUTDIR MP3FILE1 [MP3FILE2 ...]\n");
        return sb.toString();
    }

    static void cat(String resource, Class<?> klass) throws IOException{
        byte[] myBuff = new byte[1024];
        String qName = (resource.startsWith("/"))? resource : "/de/mospace/hspiel/mp3autocut/" + resource;
        InputStream is = klass.getResourceAsStream(qName);
        if(is == null){
            throw new IllegalArgumentException("Resource " + resource + "=" + qName + " not found.");
        }
        is = new BufferedInputStream(is);
        try{
            for (int i = 0; i > -1; i = is.read(myBuff)) {
                System.err.write(myBuff, 0, i);
                System.err.flush();
            }
        } finally {
            is.close();
        }
    }

    protected void cat(String resource) throws IOException{
        cat(resource, getClass());
    }
}

