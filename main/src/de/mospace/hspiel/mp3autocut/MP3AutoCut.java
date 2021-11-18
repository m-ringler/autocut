/* Copyright (C) 2009-2012 Moritz Ringler
 * $Id: MP3AutoCut.java 153 2012-12-08 20:22:05Z ringler $
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

package de.mospace.hspiel.mp3autocut;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.FileInputStream;
import java.nio.channels.FileChannel;
import java.io.IOException;
import helliker.id3.MP3File;
import java.util.concurrent.*;
import java.util.List;
import java.util.ArrayList;
import org.xml.sax.InputSource;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathExpressionException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.net.URL;
import java.net.URLConnection;
import java.net.MalformedURLException;
import java.net.URLClassLoader;

/** <p>Cuts constant bitrate mp3 files based on audio patterns stored in a special directory.
    You can cut the audio files directly or generate project files for
    mp3DirectCut, which runs under Windows and Linux with wine. In mp3DirectCut you
    can adjust the cutpoints interactively.</p>
    <p>MP3AutoCut objects cache search patterns internally.</p>
    <p>MP3AutoCut can only handle constant bitrate (CBR) mp3s.</p>

    @see <a href="http://mpesch3.de1.cc/mp3dc.html">mp3DirectCut</a>
*/
public class MP3AutoCut{
    /** The XPath expression used to retrieve the hoerdat xml element containing
        the duration of the audio recording.
     **/
    public final static String HOERDAT_XPATH = "/hoerdat:Hoerdat/hoerdat:Sendung[1]/hoerdat:Produktion";

    /** The regular expression used to extract the duration from the hoerdat xml
        element at HOERDAT_XPATH */
    public final static String HOERDAT_REGEX = "(\\bca\\.)?\\s+(\\d+)\\s+Min\\.";

    /** The compiled regular expression used to extract the duration from the hoerdat xml
        element at HOERDAT_XPATH */
    private final static Pattern HOERDAT_RE = Pattern.compile(HOERDAT_REGEX);

    private final static NamespaceContext HOERDAT_NC = new NamespaceContext() {
        @Override
        public String getNamespaceURI(String prefix) {
            if (prefix == null) throw new NullPointerException("Null prefix");
            else if ("hoerdat".equals(prefix)) return "http://hspiel.mospace.de/hoerdat/1";
            else if ("xml".equals(prefix)) return XMLConstants.XML_NS_URI;
            return XMLConstants.NULL_NS_URI;
        }

        // This method isn't necessary for XPath processing.
        @Override
        public String getPrefix(String uri) {
            throw new UnsupportedOperationException();
        }

        // This method isn't necessary for XPath processing either.
        @Override
        @SuppressWarnings("rawtypes")
        public Iterator getPrefixes(String uri) {
            throw new UnsupportedOperationException();
        }
    };

    /** Safety margin that is added to exact hoerdat durations. In minutes. */
    private final static float HOERDAT_EXTRA_MINS;

    /** Safety margin that is added to approximate hoerdat durations. In minutes. */
    private final static float HOERDAT_EXTRA_MINS_CA;
    
    static {
        String extra = System.getProperty("HoerdatExtraMins");
        HOERDAT_EXTRA_MINS = (extra == null)
            ? 0.0f
            : Float.parseFloat(extra);
        
        extra = System.getProperty("HoerdatExtraMinsCa");
        HOERDAT_EXTRA_MINS_CA = (extra == null)
            ? HOERDAT_EXTRA_MINS
            : Float.parseFloat(extra);
    }

    /** constant for the beginning of a cut */
    private final static int START = 0;

    /** constant for the end of a cut */
    private final static int END = 1;

    public final static String DEFAULT_MARKER_URL =
            "http://hspiel.mospace.de/mp3autocut/defaultMarkers.jar";

    private final static String version = loadVersion();

    private static String loadVersion(){
        try{
            Properties p = new Properties();
            p.load(MP3AutoCut.class.getResourceAsStream("version.properties"));
            return p.getProperty("version") + " (build " + p.getProperty("build") + ")";
        } catch (Exception ex){
            ex.printStackTrace();
            return "";
        }
    }

    /** whether to write cropped files */
    private final boolean crop;

    /** whether to write MP3DirectCut project files */
    private final boolean bWriteMPD;

    /** cache for searches */
    private final Map<String, MultiStepSearch> searchCache
            = new TreeMap<String, MultiStepSearch>();

    /** Search strategies for start and end. */
    private final SearchStep[][] strategies =
        new SearchStep[][]{
            MultiStepSearch.getInStrategy(),
            MultiStepSearch.getOutStrategy()
        };

    private final ClassLoader markerLoader;

    /** Constructs a new MP3AutoCut that produces both mp3DirectCut project files and cropped mp3s. */
    public MP3AutoCut(){
        this(true, true);
    }

    /** Constructs a new MP3AutoCut object.
    * @param crop whether the cut method generates cropped audio files
    * @param bWriteMPD whether the cut method produces mp3DirectCut project files
    *
    * @see #cut(File, File)
    */
    public MP3AutoCut(boolean crop, boolean bWriteMPD){
        this.crop = crop;
        this.bWriteMPD = bWriteMPD;
        try{
            downloadDefaultMarkers();
        } catch (Exception ex){
            System.err.println(ex.getMessage());
        }
        try{
            File defaultMarkers = new File(getSettingDirectory(), "defaultMarkers.jar");
            List<URL> markerLocations = new ArrayList<URL>();
            markerLocations.add(getMarkerDirectory().toURI().toURL());
            if (defaultMarkers.isFile()){
                markerLocations.add(defaultMarkers.toURI().toURL());
            }
            markerLoader = new URLClassLoader(
                markerLocations.toArray(new URL[markerLocations.size()]),
                String.class.getClassLoader()
            );

        } catch (MalformedURLException urlx){
            throw new Error(urlx);
        }
    }


    private static synchronized void downloadDefaultMarkers() throws IOException{
        File f = new File(getSettingDirectory(), "defaultMarkers.jar");
        long age = (f.exists())? f.lastModified() : Long.MIN_VALUE;
        download(new URL(DEFAULT_MARKER_URL),
            new File(getSettingDirectory(), "defaultMarkers.jar"));
        long newage = (f.exists())? f.lastModified() : Long.MIN_VALUE;
        if (newage > age){
            File[] patterns = getMarkerDirectory().listFiles();
            /* delete compiled patterns from marker directory */
            for (File p : patterns) {
                if (p.getName().endsWith(".pattern")){
                    p.delete();
                }
            }
        }
    }

    private static void download(URL url, File target) throws IOException{
        final URLConnection uc = url.openConnection();
        uc.setConnectTimeout(500);
        uc.setReadTimeout(500);

        /* setConnectTimeout does not work properly. Therefore we
           use another thread and time that thread. */
        //uc.connect();
        final ExecutorService connect = Executors.newSingleThreadExecutor();
        try{
            connect.submit(new Callable<Object>(){
                    @Override
                    public Object call() throws Exception{
                        uc.connect();
                        return null;
                    }
            }).get(500, TimeUnit.MILLISECONDS);
        } catch (Exception ex){
            throw new IOException("Failed to connect to " + url, ex);
        } finally {
            connect.shutdown();
        }

        final long urlDate = uc.getLastModified();
        if( !target.exists() || urlDate > target.lastModified()){
            System.err.println("Copying " + url + " to " + target);
            final InputStream in = uc.getInputStream();
            final File tmp = new File(System.getProperty("java.io.tmpdir"), target.getName());
            try {
                FileOutputStream out = new FileOutputStream(tmp);
                try{
                    byte[] buff = new byte[4096];
                    for (int bread = in.read(buff);
                             bread != -1;
                             bread = in.read(buff))
                    {
                        out.write(buff, 0, bread);
                    }
                } finally {
                    out.close();
                }
            } finally {
                try{
                    in.close();
                } catch (IOException ex){
                    //ignore
                }
            }
            target.delete();
            tmp.renameTo(target);
            target.setLastModified(urlDate);
        }
    }

    /** Returns whether the cut method generates cropped audio files.
    *  @return whether the cut method generates cropped audio files
    *
    * @see #MP3AutoCut(boolean crop, boolean bWriteMPD)
    */
    public boolean getCrop(){
        return crop;
    }

    /** Returns whether the cut method produces mp3DirectCut project files
    * @return whether the cut method produces mp3DirectCut project files
    *
    * @see #MP3AutoCut(boolean crop, boolean bWriteMPD)
    **/
    public boolean getWriteMPD(){
        return bWriteMPD;
    }


    /** Cuts multiple mp3 files.
    This method uses all available processors and is therefore
    much faster than repeatedly invoking the single-argument cut method
    on multicore machines.

    @param mp3 the input files to process
    @param outputdir where to store the cropped mp3s

    @see #cut(File, File)
    **/
    public void cut(File[] mp3, File outputdir) throws InterruptedException{
        int numThreads =
            Math.min(Runtime.getRuntime().availableProcessors(), mp3.length);
        final String pMaxThreads = System.getProperty("maxThreads");
        if (pMaxThreads != null){
            numThreads = Math.min(Integer.valueOf(pMaxThreads), numThreads);
        }
        final ExecutorService exec = Executors.newFixedThreadPool(numThreads);
        for (File f : mp3){
            exec.execute(new CutJob(f, outputdir));
        }
        exec.shutdown();
        exec.awaitTermination(mp3.length * 5, TimeUnit.MINUTES);
    }

    /** Cuts a single MP3 audio file.
    The cutpoints are determined by matching a start and an end pattern
    against the input.
    <p>
    The patterns for a particular mp3 file are chosen based on the file name.
    <ol>
        <li>A trailing [YYYY]-[MM]-[DD].mp3 is removed from the mp3 file name.</li>
        <li>A suffix is appended: for the start pattern "start" for the end pattern "end".</li>
        <li>The method looks in the marker directory for a pattern with this name
            (case insensitive) and .pattern extension. The .pattern files can
            be thought of as a compiled form of the audio pattern. They are
            generated by MP3AutoCut whenever a new mp3 pattern is read.
            <em>If you change an mp3 audio pattern you must remove the
            corresponding .pattern file</em>.</li>
         <li>If no .pattern file is found MP3AutoCut looks for a pattern with
             .mp3 extension.</li>
    </ol>
    </p>
    <p>
    The start cutpoint is immediately after the first occurrence of the
    start pattern or at the beginning of the input if there is no start pattern
    or if no occurrence of the start pattern is found.</p>
    <p>
    The end cutpoint is immediately before the first occurrence
    of the end pattern or at the end of the input if there is no end pattern or if
    no occurrence of the end pattern is found.
    </p>
    <p>
    When the position of the cut points has been determined a cropped
    mp3 file is created and an eponymous
    mp3DirectCut project file is generated in the directory of the input file.
    This project file can be dragged onto the mp3DirectCut window to open it.
    Both outputs can be turned off in the constructor.</p>

    @param in the input mp3 file
    @param out the location of the cropped audio file.
               Can be <code>null</code> if {@link #getCrop crop} is false.

     @see #cut(File[], File)
     @see #getMarkerDirectory
    **/
    public void cut(File in, File out) throws IOException, MP3SearchException{
        MP3Position start = getStart(in);
        MP3Position end = getEnd(in, start);

        final int tstart = (start == null) ? 0 : start.getTimeMillis();
        final int tend   = (end == null) ? Integer.MAX_VALUE : end.getTimeMillis();
        if(tend <= tstart){
            throw new MP3SearchException("Error cutting file " + in +
                ": Begin of cut (" + (tstart/1000.0) +
                "  s) is later than end of cut (" +
                (tend/1000.0) + " s).");
        }

        final long offsetStart = (start == null) ? 0l : start.getByteOffset();
        final long length = in.length();
        long offsetEnd = (end == null) ? length : end.getByteOffset();
        if(offsetStart != 0l || offsetEnd < length){
            offsetEnd = Math.min(offsetEnd, length);
            System.out.printf(
                (Locale) null,
                "%s %.2f %.2f %d %d %02d.%02d.%02d %02d.%02d.%02d \n",
                in.getName(),
                tstart/1000.,
                tend/1000.,
                offsetStart,
                offsetEnd,
                tstart/60000, // whole minutes
                (tstart/1000) % 60, // whole seconds
                (tstart % 1000)/10, // centiseconds
                tend/60000,
                (tend/1000) % 60,
                (tend % 1000)/10);

            /* write mp3directcut cue sheet */
            if (bWriteMPD){
                writeMPD(toMP3File(in), offsetStart, offsetEnd);
            }

            /* do cut */
            if (crop){
                final long bytesToTransfer = offsetEnd - offsetStart;
                FileChannel cin = (new FileInputStream(in)).getChannel();
                try{
                    FileChannel cout = (new FileOutputStream(out)).getChannel();
                    try{
                        if(bytesToTransfer != cin.transferTo(offsetStart, bytesToTransfer, cout)){
                            throw new IOException("Not all bytes copied.");
                        }
                    } finally {
                        cout.close();
                    }
                } finally {
                    cin.close();
                }
            }
        }  else {
            System.err.println(in.getName() + " No cutpoints found.");
        }
    }

    /** Writes an mp3DirectCut project file for the given mp3 audio file and
        selection. The project file will have the same path and name as the mp3
        file, with the extension replaced by ".mpd".
        @param mp3 The mp3 audio file
        @param selstart the beginning of the selection as a byte offset from the beginning of the file
        @param selend the end of the selection as a byte offset from the beginning of the file
      */
    private static void writeMPD(MP3File mp3, long selstart, long selend) throws IOException{
        File f = new File(mp3.getParent(), mp3.getFileName().replaceAll("\\.(.*?)$", ".mpd"));
        PrintStream out = new PrintStream(f);
        long astart = mp3.getAudioOffset();
        long aend = mp3.getFileSize();
        out.println("file_beg=" + astart);
        out.println("file_end=" + aend);
        out.println("file=" + mp3.getFileName());
        out.println("part_file=0");
        out.println("part_filepos=" + astart);
        out.println("part_iscue=1");
        out.println("part_size=" + (aend - astart));
        out.println("part_beg=0");
        out.println("gainend=0");
        out.println("position=" + selstart);
        out.println("vbr=0");
        out.println("sel_beg=" + selstart);
        out.println("sel_end=" + selend);
        out.flush();
        out.close();
    }

    /** Returns the position in the mp3 file where the start pattern is found.
    See {@link #cut(File, File)} for a description of how the start pattern is
    chosen. The audio pattern is searched in a {@link MultiStepSearch} near the
    beginning of the input.
     @return the time in seconds from the beginning of the audio at which the
             start pattern has been found or 0.0f if there is no start marker
             for this mp3 or if the start pattern has not been found in the
             audio.
    */
    public MP3Position getStart(File mp3) throws MP3SearchException, IOException{
        MultiStepSearch search = getSearch(mp3, START);
        MP3Position result = null;

        if(search != null){
            SearchResult searchResult = search.search(mp3);
            if (searchResult != null){
                result = searchResult.getEnd();
            }
        }
        return result;
    }

    public MP3Position getEnd(File mp3, MP3Position start) throws MP3SearchException, IOException{
        MP3Position result = getEnd(mp3);
        int tend = (result == null) ? Integer.MAX_VALUE : result.getTimeMillis();
        final File hoerdatxml = new File(mp3.getAbsolutePath().replaceAll("\\.[mM][pP]3$", ".xml"));

        if (start != null && hoerdatxml.isFile()){
            int hoerdatEndTime = getHoerdatEndMillis(hoerdatxml, start);
            if(hoerdatEndTime < tend){
                System.err.println("Using hoerdat duration for " + mp3.getName());
                result = MP3Position.getPositionForTime(hoerdatEndTime, mp3);
            }
        }

        return result;
    }

    public int getHoerdatEndMillis(File hoerdatxml, MP3Position start){
        int result = Integer.MAX_VALUE;

        try{
            /* get content of the Produktion element from hoerdat xml */
            final XPath xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(HOERDAT_NC);
            final String produktion = xpath.evaluate(
                HOERDAT_XPATH,
                new InputSource(hoerdatxml.toURI().toURL().toString()));

            /* extract the duration and return tstart + duration + safety margin */
            if (produktion != null){
                Matcher m = HOERDAT_RE.matcher(produktion);
                if (m.find()){
                    final String ca = m.group(1);
                    final String durationMins = m.group(2);
                    result = Integer.parseInt(durationMins);
                    /* safety margin: HOERDAT_EXTRA_MINS if duration is exact,
                    HOERDAT_EXTRA_MINS_CA if duration is approximate */
                    result += (ca == null)
                        ? HOERDAT_EXTRA_MINS
                        : HOERDAT_EXTRA_MINS_CA;
                    result = result * 60000 + start.getTimeMillis();
                }
            }
        } catch (XPathExpressionException xpx){
            throw new Error(xpx);
        } catch (IOException iox){
            System.err.println("Error reading hoerdat xml file " + hoerdatxml +
                ":\n\t" + iox);
        }

        return result;
    }

    /** Returns the position in the mp3 file where the end pattern is found.
    See {@link #cut(File, File)} for a description of how the end pattern is
    chosen. The audio pattern is searched in a {@link MultiStepSearch} near the
    end of the input.
     @return the time in seconds from the end of the audio at which the
             end pattern has been found or 0.0f if there is no end marker
             for this mp3 or if the end pattern has not been found in the
             audio.
    */
    public MP3Position getEnd(File mp3) throws MP3SearchException, IOException{
        final MultiStepSearch search = getSearch(mp3, END);
        MP3Position result = null;

        if(search != null){
            final SearchResult searchResult = search.search(mp3);
            if(searchResult != null){
                result = searchResult.getStart();
            }
        }

        return result;
    }

    /** Loads the specified search from the marker directory.
     @param key the name of the search, usually a cropped mp3 file name
     @param which either {@link #START} or {@link #END}

     @return the search for the given parameters of null if no such search is found
    */
    private MultiStepSearch loadSearch(String key, int which)
            throws IOException, MP3SearchException{
        MultiStepSearch search = null;
        final File markerDir = getMarkerDirectory();
        //System.err.println(java.util.Arrays.asList(((URLClassLoader) markerLoader).getURLs()));

        /* Look for ".pattern" marker in marker directory */
        URL markerURL = markerLoader.getResource(key + ".pattern");

        /* Look for ".mp3" marker in marker directory */
        if (markerURL == null){
            markerURL = markerLoader.getResource(key + ".mp3");
        }

        /* Look for default ".pattern" marker in defaultMarkers.jar */
        if (markerURL == null){
            markerURL = markerLoader.getResource("markers/" + key + ".pattern");
        }

        /* Look for default ".pattern" marker in mp3autocut.jar */
        if(markerURL == null){
            markerURL = getClass().getClassLoader().getResource("markers/" + key + ".pattern");
        }

        /* Extract ".pattern" marker from jar to marker directory */
        if (markerURL != null && "jar".equals(markerURL.getProtocol())){
            final File extracted = new File(markerDir, key + ".pattern");
            try{
                download(markerURL, extracted);
                markerURL = extracted.toURI().toURL();
            } catch (IOException ex){
                System.err.println(ex);
                markerURL = null;
                if (extracted.isFile()){
                    extracted.delete();
                }
            }
        }

        /* No markers. Return null. */
        if (markerURL == null){
            System.err.println("No marker " +  key +".*");
        } else {
            File f = null;
            try{
                f = new File(markerURL.toURI());
            } catch (java.net.URISyntaxException ex){
                throw new Error(ex);
            }
            search = new MultiStepSearch(f,  strategies[which]);
            //System.err.println("Loading " + f.getName());
            if (f.getName().endsWith(".mp3")){
                /* Serialize processed marker */
                File patternFile = new File(markerDir, key + ".pattern");
                try{
                    search.writePattern(patternFile);
                } catch (Exception ex){
                    System.err.println(ex);
                    /* delete pattern file if serialization failed.*/
                    if (patternFile.isFile()){
                        patternFile.delete();
                    }
                }
            }
        }

        return search;
    }

    /** Gets the specified search from the search cache or the marker directory.
     @param key the name of the search, usually a cropped mp3 file name
     @param which either {@link #START} or {@link #END}
     @return the search for the given parameters of null if no such search is found
    */
    private synchronized MultiStepSearch getSearch(File mp3, int which)
            throws IOException, MP3SearchException{
        final String key = mp3.getName().replaceAll("\\d{4}\\-\\d{2}\\-\\d{2}\\.mp3$", "")
        + ( (which == START)? "start" : "end" );
        if(!searchCache.containsKey(key)){
            searchCache.put(key, loadSearch(key, which));
        }
        return searchCache.get(key);
    }

    /** Clears the internal search cache. Forces all start and end
    patterns to be reloaded. */
    public synchronized void clearSearchCache(){
        searchCache.clear();
    }

    /** Returns the directory in which MP3AutoCut looks for start and end patterns.
    Tries to create the directory if it does not exist.
    @return the directory for start and end patterns
    */
    public static File getMarkerDirectory(){
        final File f = new File(getSettingDirectory(), "markers");
        synchronized (MP3AutoCut.class){
            if (!f.exists()){
                f.mkdirs();
            }
        }
        return f;
    }

    /** Returns the directory in which application settings are stored.
    Tries to create the directory if it does not exist.
    @return the MP3AutoCut setting directory
    */
    private static File getSettingDirectory(){
        String result = null;
        /* System.getenv throws a java.lang.Error in Java 1.4
         * therefore we need to protect the call to System.getenv */
         try{
             result = System.getenv("APPDATA");
         } catch (Throwable ignore){
             // handled below.
         }

        if(result == null){
            result = System.getProperty("user.home") + File.separator + ".mp3autocut";
        } else {
            result += File.separator + "MP3AutoCut";
        }
        final File f = new File(result);
        synchronized (MP3AutoCut.class){
            if (!f.exists()){
                f.mkdirs();
            }
        }
        return f;
    }

    /** Returns a new MP3File for the given abstract path.
        The checked exceptions thrown by the MP3File constructor are wrapped
        as MP3SearchExceptions.

        @throws MP3SearchException if the MP3File constructor throws a checked exception
            or if the MP3File is a variable bitrate (VBR) mp3
        @see helliker.id3.MP3File#MP3File
    */
    static MP3File toMP3File(File mp3)  throws MP3SearchException, IOException{
        MP3File result = null;
        try{
            result = new MP3File(mp3);
        } catch (helliker.id3.NoMPEGFramesException ex){
            throw new MP3SearchException(ex);
        } catch (helliker.id3.ID3v2FormatException ex){
            throw new MP3SearchException(ex);
        } catch (helliker.id3.CorruptHeaderException ex){
            throw new MP3SearchException(ex);
        }
        if (result.isVBR()) {
            throw new MP3SearchException(mp3.getPath() + " is a variable bitrate (VBR) mp3." +
                "Only constant bitrate (CBR) mp3 files are allowed.");
        }
        return  result;
    }


    public static String getVersion(){
        return version;
    }

    /** Starts the MP3AutoCut application.
    *   MP3 files are constant bit rate mp3 audio files.
    */
    public static void main(String[] argv) {
        MP3AutoCutCmdLine cmdline = null;
        System.err.println("MP3AutoCut " + getVersion() + "; (c) 2009-2012, Moritz Ringler");
        MP3AutoCutCmdLine.printGPL();
        System.err.println();

        try{
            cmdline = new MP3AutoCutCmdLine(argv);
            System.err.println("Patterns are read from " + getMarkerDirectory());
            MP3AutoCut cutter = new MP3AutoCut(cmdline.getWriteMP3(),
                cmdline.getWriteMPD());
            cutter.cut(cmdline.getMP3s(), cmdline.getOutputDir());
        } catch (Exception ex){
            MP3AutoCutCmdLine.printHelp();
            System.err.println();
            System.err.println(MP3AutoCutCmdLine.getUsage());
            System.err.println("Patterns are read from " + getMarkerDirectory());
            System.err.println("\nERROR:");
            System.err.println(ex.getLocalizedMessage());
        }

    }

    /** Wraps a single call to the {@link cut(File, File)} method.
    Exceptions thrown by cut are caught and printed on System.err.
    */
    private class CutJob implements Runnable{
        private final File mp3;
        private final File outputdir;

        public CutJob(File mp3, File outputdir){
            this.mp3 = mp3;
            this.outputdir = outputdir;
        }

        @Override
        public void run(){
            try{
                cut(mp3, (outputdir == null)
                    ? null
                    : new File(outputdir, mp3.getName())
                 );
            } catch (Exception ex){
                ex.printStackTrace();
                System.err.println(ex);
            }
        }
    }
}
