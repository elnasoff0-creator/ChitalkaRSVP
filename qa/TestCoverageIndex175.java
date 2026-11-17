package ru.reader.rsvp;
import java.util.*;
public class TestCoverageIndex175 {
 public static void main(String[] args){
  Random random=new Random(175);int checks=0;
  for(int k=0;k<100;k++){
   ArrayList<EdgeOfflineIndex.Segment> clips=new ArrayList<>();
   for(int j=0;j<300;j++){
    int start=random.nextInt(20000),end=start+random.nextInt(250)+1;
    clips.add(new EdgeOfflineIndex.Segment(start,end,"id"+j,10+j));
   }
   clips.sort(Comparator.comparingInt(a->a.start));
   EdgeOfflineIndex.CoverageIndex fast=new EdgeOfflineIndex.CoverageIndex(clips);
   for(int t=0;t<100;t++){
    int from=random.nextInt(20000),to=from+random.nextInt(1000);
    EdgeOfflineIndex.Stats expected=EdgeOfflineIndex.stats(clips,from,to),actual=fast.stats(from,to);
    if(expected.covered!=actual.covered||expected.files!=actual.files||expected.bytes!=actual.bytes)throw new AssertionError("index mismatch "+from+":"+to);
    checks++;
   }
  }
  System.out.println("PASS: "+checks+" Edge coverage index comparisons");
 }
}
