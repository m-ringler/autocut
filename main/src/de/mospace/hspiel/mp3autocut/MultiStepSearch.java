/* Copyright (C) 2009 Moritz Ringler
 * $Id: MultiStepSearch.java 78 2010-12-13 20:36:22Z ringler $
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

import java.io.File;
import java.io.IOException;

public class MultiStepSearch{
    private final MP3Search searcher;
    private SearchStep[] strategy;

    public MultiStepSearch(File mp3pattern, SearchStep[] strategy)
    throws IOException, MP3SearchException{
        if(strategy.length < 1){
            throw new IllegalArgumentException("Empty strategy is not allowed.");
        }
        this.strategy = strategy.clone();
        searcher = new MP3Search(mp3pattern);
    }

    public void writePattern(File f) throws IOException{
        searcher.writePattern(f);
    }

    public SearchResult search(File mp3) throws MP3SearchException, IOException{
        SearchResult result = null;
        SearchResult stepResult = null;
        final int totalTime = MP3AutoCut.toMP3File(mp3).getPlayingTime() * 1000;

        for (SearchStep step : strategy){
            final int ti = step.getStartTimeMillis();
            final int tf = step.getEndTimeMillis();
            stepResult = searcher.searchStep(mp3,
                (ti >= 0)? ti : Math.max(totalTime + ti, 0) ,
                (tf >= 0)? tf : Math.max(totalTime + tf, 0));
            if(stepResult != null && stepResult.getCorrelation() >= step.getSuccessCorrelation()){
                result = stepResult;
                break;
            }
        }

        if (result != null)
        {
            MP3Position[] positions = MP3Position.getPositionsForFrameCounts(
                new int[]{ result.getStart().getFrameCount(), result.getEnd().getFrameCount() },
                mp3);
            result = new SearchResult(positions[0], positions[1], result.getCorrelation());
        }

        return result;
    }

    public void setStrategy(SearchStep[] strategy){
        this.strategy = strategy.clone();
    }

    public SearchStep[] getStrategy(){
        return this.strategy.clone();
    }

    public static SearchStep[] getInStrategy(){
        return new SearchStep[]{
            new SearchStep(0.8f,      0, 300000),
            new SearchStep(0.9f, 290000, 600000),
            new SearchStep(0.7f,      0, 300000),
            new SearchStep(0.8f, 290000, 600000)
            //new SearchStep(0.0f, 0, Float.POSITIVE_INFINITY)
        };
    }

    public static SearchStep[] getOutStrategy(){
        return new SearchStep[]{
            new SearchStep(0.80f, - 600000, Integer.MAX_VALUE),
            new SearchStep(0.85f, -1200000, -590000),
            new SearchStep(0.70f, - 900000, Integer.MAX_VALUE)
        };
    }
}
