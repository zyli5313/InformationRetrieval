package invertidx;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;

import query.QueryResult;
import query.ResultEntry;
import query.Util;

/**
 * InvertedIndex: class for inverted index (posting lists)
 * 
 * @author Zeyuan Li
 * */
public class InvertedIndex {

  // public String path;
  public String term;

  public String termStem;

  public double ctf; // collection term frequency

  public double ttc; // total term count
  
  public double df; // document frequency

  public ArrayList<DocEntry> docEntries; // a list of doc entries

  public InvertedIndex() {
    docEntries = new ArrayList<DocEntry>();
  }

  public InvertedIndex(String path) {
    docEntries = new ArrayList<DocEntry>();
    readInvertedIndex(path);
  }

  public void readInvertedIndex(String path) {
    try {
      BufferedReader br = new BufferedReader(new FileReader(path));
      String line = br.readLine();
      String[] strs = line.split("\\s+");
      if(strs.length != 5)  // bad line
        return;
      
      term = strs[0];
      termStem = strs[1];
      ctf = Double.parseDouble(strs[2]);

      assert strs.length == 5 : path + " " + line;
      ttc = Double.parseDouble(strs[3]);
      df = Double.parseDouble(strs[4]);
      
      while ((line = br.readLine()) != null) {
        strs = line.split("\\s+");
        ArrayList<Integer> poslist = new ArrayList<Integer>();

        for (int i = 3; i < strs.length; i++)
          poslist.add(Integer.parseInt(strs[i]));

        DocEntry de = new DocEntry(Integer.parseInt(strs[0]), Integer.parseInt(strs[1]),
                Integer.parseInt(strs[2]), poslist);
        docEntries.add(de);
      }
      
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }


  /**
   * NEAR/k query
   * */
  public void near(InvertedIndex ii, int k, int rankType) {
    if (ii == null)
      return;

    int io = 0, idoc = 0, no = ii.docEntries.size(), ndoc = docEntries.size();
    double ctfnew = 0.0;
    ArrayList<DocEntry> mergeres = new ArrayList<DocEntry>();
    
    while (io < no && idoc < ndoc) {
      DocEntry en = docEntries.get(idoc);
      DocEntry eno = ii.docEntries.get(io);

      if (en.docid == eno.docid) {
        List<Integer> p1 = en.pos, p2 = eno.pos;
        List<Integer> pres = new ArrayList<Integer>(); // possible result positions
        
        int j1 = 0, j2 = 0, n1 = p1.size(), n2 = p2.size();
        while(j1 < n1 && j2 < n2) {
          if(p1.get(j1) > p2.get(j2))
            j2++;
          else {
            // find a match
            if(p2.get(j2) - p1.get(j1) <= k) {
              pres.add(p2.get(j2));
              j1++;
              j2++;
            }
            else
              j1++;
          }
        }
        
        if(pres.size() > 0) {
          en.pos = pres;
          en.tf = pres.size();
          ctfnew += en.tf;
          mergeres.add(en);
        }
        
        io++;
        idoc++;
      } else if (en.docid < eno.docid) {
        idoc++;
      } else
        io++;
    }// end while
    
    // assign new list and update df, ctf
    docEntries = mergeres;
    df = docEntries.size();
    ctf = ctfnew;
  }
  
  public void uw(ArrayList<InvertedIndex> iis, int k, int rankType) {
    int n = iis.size();
    int[] idx = new int[n];
    double ctfnew = 0.0;
      
    ArrayList<DocEntry> mergeres = new ArrayList<DocEntry>();
    boolean end = false;
    
    while(!end) {
      // every time find min docid among inverted indices
      int idxmin = 0;
      
      int mindocid = iis.get(0).docEntries.get(idx[0]).docid;
      boolean issame = true;
      
      for(int i = 1; i < n; i++) {
        int curdocid = iis.get(i).docEntries.get(idx[i]).docid;
        if(curdocid != mindocid)
          issame = false;
        
        if(curdocid < mindocid) {
          mindocid = curdocid;
          idxmin = i;
        }
      }
      
      if(!issame) {
        idx[idxmin]++;
        // some list reach the end
        if(idx[idxmin] == iis.get(idxmin).docEntries.size())
          break;
      }
      // same docids, merge posting lists
      else {
        // invariant: iis.get(i).docEntries.get(idx[i]).docid are the same
        int[] idxpos = new int[n];
        List<Integer> pres = new ArrayList<Integer>();
        boolean endpos = false;
        
        while(!endpos) {
          int imin = 0, imax = 0;
          int minpos = iis.get(0).docEntries.get(idx[0]).pos.get(idxpos[0]), maxpos = minpos;
          
          for(int i = 1; i < n; i++) {
            if(idxpos[i] == iis.get(i).docEntries.get(idx[i]).pos.size())
              System.out.println("asdasd");
            assert idxpos[i] < iis.get(i).docEntries.get(idx[i]).pos.size() : i+" "+idx[i]+" "+idxpos[i];
            
            int curpos = iis.get(i).docEntries.get(idx[i]).pos.get(idxpos[i]);
            if(curpos < minpos) {
              minpos = curpos;
              imin = i;
            }
            if(curpos > maxpos) {
              maxpos = curpos;
              imax = i;
            }
          }
          
          // judge based on k
          // invalid match
          if(1 + maxpos - minpos > k) {
            idxpos[imin]++;
            if(idxpos[imin] == iis.get(imin).docEntries.get(idx[imin]).pos.size()) {
              endpos = true;
              break;
            }
          }
          else {
            pres.add(minpos);
            // advance all idxpos[]
            for(int i = 0; i < n; i++) {
              idxpos[i]++;
              if(idxpos[i] == iis.get(i).docEntries.get(idx[i]).pos.size()) {
                endpos = true;
                break;
              }
            }
          }
        }
        
        // add hullucinated inverted index to merge list
        if(pres.size() > 0) {
          DocEntry en = iis.get(idxmin).docEntries.get(idx[idxmin]);
          en.pos = pres;
          en.tf = pres.size();  // update tf
          
          ctfnew += en.tf;
          mergeres.add(en);
        }
        
        // advance all idx[] pointers
        for(int i = 0; i < n; i++) {
          idx[i]++; 
          if(idx[i] == iis.get(i).docEntries.size()) {
            end = true;
            break;
          }
        }
      }
    }
    
    // assign new list and update df, ctf
    docEntries = mergeres;
    df = docEntries.size();
    ctf = ctfnew;
  }

}
