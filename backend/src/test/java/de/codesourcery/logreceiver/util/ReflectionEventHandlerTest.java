package de.codesourcery.logreceiver.util;

import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ReflectionEventHandlerTest
{
    private ReflectionEventHandler handler;

    private static class Event1 implements IEvent {}
    private static class Event2 extends Event1 {}
    private static class Event3 implements IEvent {}

    private static final class Invalid1 {
        @Subscribe
        public static void test(IEvent ev) { }
    }

    private static final class Invalid2 {
        @Subscribe
        private void test(IEvent ev) { }
    }

    private static final class Invalid3 {
        @Subscribe
        private void test() { }
    }

    private static final class Invalid4 {
        @Subscribe
        public boolean test(IEvent event) { return true; }
    }

    private static final class Invalid5 {
        @Subscribe
        public void test(IEvent event,boolean test) {}
    }

    private static final class Test1 {
    }

    private static final class Test2
    {
        public IEvent event;

        @Subscribe
        public void handle(IEvent event) {
            this.event = event;
        }
    }

    private static final class Test3
    {
        public IEvent event;
        public IEvent event1;
        public IEvent event2;

        @Subscribe
        public void handle(IEvent event) {
            this.event = event;
        }

        @Subscribe
        public void handle1(Event1 event) {
            this.event1 = event;
        }

        @Subscribe
        public void handle2(Event2 event) {
            this.event2 = event;
        }
    }

    @Test
    public void testHandleSimpleEvent() throws Exception
    {
        final Test2 instance = new Test2();
        handler = new ReflectionEventHandler( () -> instance );
        final Event1 ev = new Event1();
        assertTrue( handler.handleEvent( ev ) );
        assertSame(ev, instance.event );
    }

    @Test
    public void testHandleDerivedEvent() throws Exception
    {
        final Test2 instance = new Test2();
        handler = new ReflectionEventHandler( () -> instance );
        final Event2 ev = new Event2();
        assertTrue( handler.handleEvent( ev ) );
        assertSame(ev, instance.event );
    }

    @Test
    public void testUsesMostSpecificMethod1() throws Exception
    {
        final Test3 instance = new Test3();
        handler = new ReflectionEventHandler( () -> instance );
        final IEvent ev = new Event2();
        assertTrue( handler.handleEvent( ev ) );
        assertNull( instance.event1 );
        assertSame( ev, instance.event2 );
        assertNull( instance.event );
    }

    @Test
    public void testUsesMostSpecificMethod2() throws Exception
    {
        final Test3 instance = new Test3();
        handler = new ReflectionEventHandler( () -> instance );
        final IEvent ev = new Event1();
        assertTrue( handler.handleEvent( ev ) );
        assertSame( ev, instance.event1 );
        assertNull( instance.event2 );
        assertNull( instance.event );
    }

    public void testUsesMostSpecificMethod3() throws Exception
    {
        final Test3 instance = new Test3();
        handler = new ReflectionEventHandler( () -> instance );
        final IEvent ev = new Event3();
        assertTrue( handler.handleEvent( ev ) );
        assertNull( instance.event1 );
        assertNull( instance.event2 );
        assertSame( ev, instance.event );
    }

    @Test
    public void testInvalidClasses() throws Exception
    {
        testInvalid( Invalid1.class );
        testInvalid( Invalid2.class );
        testInvalid( Invalid3.class );
        testInvalid( Invalid4.class );
        testInvalid( Invalid5.class );
    }

    private void testInvalid(Class<?> clazz) throws Exception
    {
        final Object instance = clazz.newInstance();
        handler = new ReflectionEventHandler( () -> instance );
        final IEvent ev = new Event1();
        try {
            handler.handleEvent( ev );
            fail("Should've failed");
        }
        catch(Exception e) {
            // ok
        }
    }

    @Test
    public void findAnnotatedMethods()
    {
    }
}