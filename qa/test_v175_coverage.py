from pathlib import Path
import tempfile,subprocess
R=Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as td:
 t=Path(td);src=t/'src/org/json';src.mkdir(parents=True)
 (src/'JSONObject.java').write_text('package org.json;public class JSONObject {public int optInt(String k,int v){return v;} public String optString(String k,String v){return v;}}')
 (src/'JSONArray.java').write_text('package org.json;public class JSONArray {public JSONArray(){}public JSONArray(String s){}public int length(){return 0;}public JSONObject optJSONObject(int i){return null;}}')
 command=['javac','-encoding','UTF-8','-d',str(t),str(src/'JSONObject.java'),str(src/'JSONArray.java'),str(R/'app/src/main/java/ru/reader/rsvp/EdgeOfflineIndex.java'),str(R/'qa/TestCoverageIndex175.java')]
 subprocess.run(command,check=True);subprocess.run(['java','-cp',str(t),'ru.reader.rsvp.TestCoverageIndex175'],check=True)
