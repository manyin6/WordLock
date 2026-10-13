package com.wordlock.app;
public class AccessibilityComponentsTest {
    static int passed;
    static void check(String value, boolean expected) {
        boolean actual=AccessibilityComponents.contains(value,"com.wordlock.app","com.wordlock.app.GuardService");
        if (actual!=expected) throw new AssertionError("Matching failed: "+value);
        passed++;
    }
    public static void main(String[] args) {
        check(null,false); check("",false);
        check("com.wordlock.app/.GuardService",true);
        check("com.wordlock.app/com.wordlock.app.GuardService",true);
        check("other.pkg/.Service:com.wordlock.app/.GuardService",true);
        check(" com.wordlock.app/.GuardService ",true);
        check("com.wordlock.app/.OtherService",false);
        check("other.com.wordlock.app/com.wordlock.app.GuardService",false);
        check("com.wordlock.app/",false);
        check("com.wordlock.app//.GuardService",false);
        check("com.wordlock.app/.guardservice",false);
        System.out.println(passed+" component matching tests passed");
    }
}
