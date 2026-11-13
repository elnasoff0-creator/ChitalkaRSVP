package ru.reader.rsvp;
import java.util.List;

public final class TestRanobeLibCoverUrls {
    private static void equal(String a,String b){if(!a.equals(b))throw new AssertionError(a+" != "+b);}
    private static void yes(boolean b){if(!b)throw new AssertionError("Expected true");}
    private static void no(boolean b){if(b)throw new AssertionError("Expected false");}
    public static void main(String[] args){
        String path="/uploads/cover/all-the-skills-a-deckbuilding-litrpg/cover/f7197afb-2b01-4747-8742-5da2c90092a1.jpg";
        String expected="https://cover.lib.social"+path;
        equal(RanobeLibCoverUrls.normalize(expected),expected);
        equal(RanobeLibCoverUrls.normalize(path),expected);
        equal(RanobeLibCoverUrls.normalize("uploads/cover/"+path.substring("/uploads/cover/".length())),expected);
        equal(RanobeLibCoverUrls.fromCoverId("136924--all-the-skills-a-deckbuilding-litrpg","f7197afb-2b01-4747-8742-5da2c90092a1"),expected);
        List<String> mirrors=RanobeLibCoverUrls.candidateUrls("https://cover.cdnlibs.org"+path);
        yes(mirrors.size()==2 && mirrors.get(0).equals(expected));
        no(RanobeLibCoverUrls.trustedHost("attacker-cover.lib.social.evil.com"));
        equal(RanobeLibCoverUrls.normalize("https://evil.example"+path),"");
        equal(RanobeLibCoverUrls.normalize("http://cover.lib.social"+path),"");
        equal(RanobeLibCoverUrls.normalize("https://cover.lib.social.evil.com"+path),"");
        equal(RanobeLibCoverUrls.normalize("https://cover.lib.social:444"+path),"");
        equal(RanobeLibCoverUrls.normalize("javascript:alert(1)"),"");
        equal(RanobeLibCoverUrls.normalize("/uploads/chapter/sample.jpg"),"https://ranobelib.me/uploads/chapter/sample.jpg");
        System.out.println("PASS: RanobeLIB cover URLs, new CDN, old CDN, relative paths, identifiers, host restrictions");
    }
}
