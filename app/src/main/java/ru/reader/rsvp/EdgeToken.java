package ru.reader.rsvp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;

/** Edge's unofficial read-aloud time token. Unstable third-party protocol.
 * The v2 algorithm follows the public rany2/edge-tts reference (GPL-3.0).
 * Source: https://github.com/rany2/edge-tts/blob/master/src/edge_tts/drm.py
 */
final class EdgeToken {
    static final String TOKEN="6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    static final String VERSION="2-155.0.4283.45";
    private static final long MASK=0xffffffffL;
    private static final int[] K={
      0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
      0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
      0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
      0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
      0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
      0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
      0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
      0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2};
    private static final int[] INITIAL={0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19};
    private static final int[] STATE={0xfd9fbea2,0x91243ce7,0xbe499094,0xcd2e1e0a,0x6c006f49,0x04bd35ab,0xfeb0a7e4,0x03effbd2};
    private static final int[] SCHEDULE={0xeb24bf9e,0x1558691f,0xa0c09ee2,0x0cfa32ba,0xa622ee8c,0xd1dd9491,0xc0dd25a0,0xf9e70ab6,0xf0c31573,0x7058ce4e,0x7188e606,0x00e5ab33,0xd258b060,0x72766564};
    private static final long[][] COEFF={
      {0xe82e91b1L,0xb32700d0L},{0x44de24c6L,0x88dff51dL},{0x3beb65bcL,0x29514038L},
      {0xc89c5933L,0xb328cbdfL},{0x8d1ec211L,0xd405b4d5L},{0x314ef5d5L,0x26e55f3cL},
      {0xe92491b1L,0x607801c6L},{0xf0a5b9d5L,0x82b2c1fbL},{0x06064cf3L,0xfa51a3daL},
      {0xda79fa60L,0x207cc464L},{0xbed20560L,0x3c3c74f3L},{0x89f9c9f8L,0xd2d14b61L},
      {0x35fa38c6L,0xbb04a2c1L},{0xcb49e3c6L,0xc5626167L}};
    private EdgeToken() {}
    private static int r(int x,int bits){return Integer.rotateRight(x,bits);}
    private static int small0(int x){return r(x,7)^r(x,18)^(x>>>3);}
    private static int small1(int x){return r(x,17)^r(x,19)^(x>>>10);}
    private static void expand(int[] w,int start){for(int i=start;i<64;i++)w[i]=w[i-16]+small0(w[i-15])+w[i-7]+small1(w[i-2]);}
    private static int[] compress(int[] state,int[] w,int first,boolean special){
        int a=state[0],b=state[1],c=state[2],d=state[3],e=state[4],f=state[5],g=state[6],h=state[7];
        for(int i=first;i<64;i++){
            int t1=h+(r(e,6)^r(e,11)^r(e,25))+(g^(e&(f^g)))+(special&&i==14?0xc9dc06a7:K[i])+w[i];
            int t2=(r(a,2)^r(a,13)^r(a,22))+((a&b)|(c&(a|b)));
            h=g;g=f;f=e;e=d+t1;d=c;c=b;b=a;a=t1+t2;
        }
        return new int[]{a,b,c,d,e,f,g,h};
    }
    private static int[] init(byte[] data){
        int[] w=new int[64];
        for(int i=0;i<3;i++)w[14]=(w[14]<<8)|(data[i]&255);
        for(int i=3;i<7;i++)w[15]=(w[15]<<8)|(data[i]&255);
        for(int i=16;i<30;i++){
            int value=SCHEDULE[i-16];
            if(i-2>=14) value+=(i==16 ? small1(w[i-2])^0x000b92c0 : small1(w[i-2]));
            if(i-7>=14) value+=w[i-7];
            if(i-15>=14) value+=(i==29 ? small0(w[i-15])^0x059c0b80 : small0(w[i-15]));
            w[i]=value;
        }
        w[30]=small1(w[28])+w[23]+small0(w[15])+w[14]+0xc9dc06a7-K[14];
        expand(w,31);
        int[] raw=compress(STATE,w,14,true);
        for(int i=0;i<8;i++)raw[i]+=INITIAL[i];
        return raw;
    }
    static String gec(){return generate(System.currentTimeMillis()/1000L);}
    static String generate(long unixSec){
        long windows=(unixSec+11644473600L);windows-=(windows%300L);windows*=10000000L;
        byte[] input=(Long.toString(windows)+TOKEN).getBytes(StandardCharsets.US_ASCII);
        byte[] hash=customDigest(input);
        long[] cs=cs64(input,hash);
        StringBuilder sb=new StringBuilder(81);
        for(int i=0;i<2;i++){
            long v=cs[i];for(int k=0;k<4;k++){sb.append(String.format(Locale.ROOT,"%02X",(v>>>(k*8))&255));}
        }
        sb.append('-');for(byte b:hash)sb.append(String.format(Locale.ROOT,"%02X",b&255));
        return sb.toString();
    }
    private static byte[] customDigest(byte[] data){
        int[] state=init(data);
        int length=57+data.length;
        int zero=(55-length)%64;if(zero<0)zero+=64;
        int plen=data.length-7+1+zero+8;
        byte[] padded=new byte[plen];
        System.arraycopy(data,7,padded,0,data.length-7);
        padded[data.length-7]=(byte)0x80;
        long bits=(long)length*8;
        for(int i=0;i<8;i++)padded[plen-1-i]=(byte)(bits>>>(8*i));
        for(int p=0;p<plen;p+=64){
            int[] w=new int[64];
            for(int i=0;i<16;i++){
                int v=0;for(int j=0;j<4;j++)v=(v<<8)|(padded[p+4*i+j]&255);w[i]=v;
            }
            expand(w,16);int[] raw=compress(state,w,0,false);for(int i=0;i<8;i++)state[i]+=raw[i];
        }
        byte[] output=new byte[32];
        for(int i=0;i<8;i++)for(int j=0;j<4;j++)output[4*i+j]=(byte)(state[i]>>>(24-j*8));
        return output;
    }
    private static long rol16(long x){x&=MASK;return ((x<<16)|(x>>>16))&MASK;}
    private static long[] cs64(byte[] data,byte[] digest){
        long keyA=0,keyB=0;for(int i=0;i<4;i++){
            keyA|=(digest[i]&255L)<<(8*i);keyB|=(digest[i+4]&255L)<<(8*i);
        }
        keyA|=1;keyB|=1;
        long[] factor={keyA*0x82893933L&MASK,keyB*0x82893933L&MASK};
        java.util.ArrayList<Long> terms=new java.util.ArrayList<>(), offsets=new java.util.ArrayList<>();
        for(int i=0;i<COEFF.length;i++){terms.add(COEFF[i][0]*factor[i%2]&MASK);offsets.add(COEFF[i][1]);}
        byte[] withZero=new byte[data.length+1];System.arraycopy(data,0,withZero,1,data.length);
        int wordCount=(withZero.length+3)/4;if((wordCount&1)!=0)wordCount++;
        for(int i=0;i<wordCount;i++){
            long word=0;for(int j=0;j<4;j++){int at=i*4+j;if(at<withZero.length)word|=(withZero[at]&255L)<<(j*8);}
            terms.add(word*(i%2==0?keyA:keyB)&MASK);
            long offset=rol16(word)*(i%2==0?0xef0569fbL:0xc31713dbL)&MASK;
            if(i==0){terms.set(COEFF.length, (terms.get(COEFF.length)+0xf2f90b1aL*factor[0])&MASK);offset=(offset+0x0b1a0000L)&MASK;}
            offsets.add(offset);
        }
        long swapAcc=0,swapLast=0,state=0,acc=0;
        for(int i=0;i<terms.size();i+=2){
            long x=terms.get(i),y=terms.get(i+1);
            long t=rol16((offsets.get(i)+x)&MASK),q=t*0x76f50f34L&MASK;
            swapAcc=(swapAcc+((rol16(q)*0xc3efeA97L+q)&MASK))&MASK;
            t=rol16((offsets.get(i+1)+y)&MASK);q=t*0x1881ce3cL&MASK;
            swapLast=(rol16(q)*0x35bd1ec9L+q)&MASK;
            swapAcc=(swapAcc+swapLast)&MASK;
            state=(state*keyA+x)&MASK;
            state=rol16(state)*0xcf98b111L&MASK;
            state=rol16(state)*0x87085b9fL&MASK;
            state=rol16(state)*0x12ceb96dL&MASK;
            state=rol16(state)*0x257e1d83L&MASK;
            acc=(acc+state)&MASK;
            t=(state*keyB+y)&MASK;
            t=rol16(t)*0xa27416f5L&MASK;
            t=rol16(t)*0xd38396ffL&MASK;
            state=rol16((rol16(t)*0x7c932b89L)&MASK)*0xbfa49f69L&MASK;
            acc=(acc+state)&MASK;
        }
        return new long[]{swapLast^state,swapAcc^acc};
    }
}
