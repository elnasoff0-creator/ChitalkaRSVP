package ru.reader.rsvp;

/** Desktop regression checks for solid body/book collisions and alpha-free margins. */
public final class TestRunnerHitboxes {
    private static void require(boolean condition, String msg) {
        if(!condition) throw new AssertionError(msg);
    }
    public static void main(String[] args) {
        RunnerHitboxes.Box visiblePet=new RunnerHitboxes.Box(100,100,160,175);
        RunnerHitboxes.Box body=RunnerHitboxes.pet(visiblePet);
        require(body.left>visiblePet.left && body.right<visiblePet.right,"wings and tail excluded");
        require(body.top>visiblePet.top && body.bottom<visiblePet.bottom,"pet outlines excluded");

        RunnerHitboxes.Box visibleBook=new RunnerHitboxes.Box(130,150,185,195);
        RunnerHitboxes.Box book=RunnerHitboxes.book(visibleBook);
        require(book.left>visibleBook.left && book.right<visibleBook.right,"book outlines excluded");
        require(body.intersects(book),"direct body hit should collide");

        RunnerHitboxes.Box transparentGap=new RunnerHitboxes.Box(0,0,20,20);
        require(!body.intersects(transparentGap),"transparent margin must not collide");
        require(!body.intersects(new RunnerHitboxes.Box(161,120,190,180)),"nonoverlap must be safe");
        require(!body.intersects(new RunnerHitboxes.Box(body.right,body.top,body.right+10,body.bottom)),"touching edges alone is safe");
        require(body.intersects(new RunnerHitboxes.Box(body.right-1,body.bottom-1,body.right+5,body.bottom+5)),"true overlap must collide");

        System.out.println("PASS runner hitboxes: pet body, book inset, empty margins, touching edges");
    }
}
