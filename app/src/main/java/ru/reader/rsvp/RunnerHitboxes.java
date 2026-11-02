package ru.reader.rsvp;

/** Numeric collision geometry shared by sprite rendering and the runner.
 * Inputs are the visible (nontransparent-alpha) bounds in world/dp coordinates.
 * The pet has a forgiving body/feet box; wings and tail are visual-only.
 */
final class RunnerHitboxes {
    private RunnerHitboxes() {}

    static final class Box {
        final float left, top, right, bottom;
        Box(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}
        float width(){return right-left;}
        float height(){return bottom-top;}
        boolean intersects(Box other){return left<other.right && right>other.left && top<other.bottom && bottom>other.top;}
    }

    static Box pet(Box alpha){
        float w=alpha.width(),h=alpha.height();
        // Only the central body and feet, not empty canvas or outstretched wings.
        return new Box(alpha.left+w*.22f,alpha.top+h*.12f,
                alpha.left+w*.78f,alpha.bottom-h*.08f);
    }

    static Box book(Box alpha){
        float w=alpha.width(),h=alpha.height();
        // Narrow the hitbox slightly so grazing the drawn outline feels fair.
        return new Box(alpha.left+w*.07f,alpha.top+h*.06f,
                alpha.right-w*.07f,alpha.bottom-h*.035f);
    }
}
