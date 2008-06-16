package rainbow.types;

import rainbow.ArcError;

import javax.swing.*;
import javax.swing.plaf.basic.BasicTextUI;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.StyleSheet;
import javax.swing.text.*;
import javax.swing.event.CaretListener;
import javax.swing.event.CaretEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.*;
import java.io.IOException;

public class JavaObject extends ArcObject {
  public static final Symbol TYPE = (Symbol) Symbol.make("java-object");
  private Object object;

  public JavaObject(Object object) {
    this.object = object;
  }

  public static ArcObject getClassInstance(String className) {
    try {
      return new JavaObject(Class.forName(className));
    } catch (ClassNotFoundException e) {
      throw new ArcError("Can't find class " + className + " : " + e.getMessage(), e);
    }
  }

  public static JavaObject instantiate(String className, Pair args) {
    try {
      Class target = Class.forName(className);
      if (args.isNil()) {
        return new JavaObject(target.newInstance());
      } else {
        Constructor c = findConstructor(target, args);
        Object[] javaArgs = unwrapList(args, c.getParameterTypes());
        return new JavaObject(c.newInstance(javaArgs));
      }
    } catch (Exception e) {
      throw new ArcError("Can't instantiate class " + className + " : " + e.getMessage(), e);
    }
  }

  private static Constructor findConstructor(Class c, Pair args) {
    int parameterCount = args.size();
    for (int i = 0; i < c.getConstructors().length; i++) {
      Constructor constructor = c.getConstructors()[i];
      if (constructor.getParameterTypes().length == parameterCount) {
        if (match(constructor.getParameterTypes(), args, 0)) {
          return constructor;
        }
      }
    }
    throw new ArcError("no constructor found matching " + args + " on " + c);
  }

  public Object invoke(String methodName, Pair args) {
    return invokeMethod(object, object.getClass(), methodName, args);
  }

  public static Object staticInvoke(String className, String methodName, Pair args) {
    return invokeMethod(null, toClass(className), methodName, args);
  }

  public ArcObject type() {
    return TYPE;
  }

  public Object unwrap() {
    return object;
  }

  public String toString() {
    return TYPE + ":" + object;
  }

  private static Class toClass(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new ArcError("Class not found: " + className + "(" + e + ")", e);
    }
  }

  public static JavaObject cast(ArcObject argument, Object caller) {
    try {
      return (JavaObject) argument;
    } catch (ClassCastException e) {
      throw new ArcError("Wrong argument type: " + caller + " expected a java-object, got " + argument);
    }
  }

  public static Object getStaticFieldValue(String className, String fieldName) {
    Class c = toClass(className);
    try {
      Field f = c.getField(fieldName);
      return f.get(null);
    } catch (NoSuchFieldException e) {
      throw new ArcError("No field " + fieldName + " exists on " + c, e);
    } catch (IllegalAccessException e) {
      throw new ArcError("Field " + fieldName + " is not accessible on " + c, e);
    }
  }

  private static Object invokeMethod(Object target, Class aClass, String methodName, Pair args) {
    Object[] javaArgs = new Object[0];
    try {
      Method method = findMethod(aClass, methodName, args.size());
      method.setAccessible(true);
      javaArgs = unwrapList(args, method.getParameterTypes());
      return method.invoke(target, javaArgs);
    } catch (IllegalArgumentException e) {
      System.out.println("arc args: " + args);
      System.out.println("java args: " + new ArrayList(Arrays.asList(javaArgs)));
      System.out.println("method: " + methodName);
      System.out.println("on class: " + aClass);
      throw e;
    } catch (IllegalAccessException e) {
      throw new ArcError("Method " + methodName + " is not accessible on " + aClass, e);
    } catch (InvocationTargetException e) {
      throw new ArcError("Invoking " + methodName + " on " + target + " with args " + args + " : " + e.getCause().getMessage(), e);
    }
  }

  private static Object[] unwrapList(Pair args, Class[] parameterTypes) {
    Object[] result = new Object[parameterTypes.length];
    if (result.length > 0) {
      unwrapList(result, parameterTypes, args, 0);
    }
    return result;
  }

  private static void unwrapList(Object[] result, Class<?>[] parameterTypes, Pair args, int i) {
    result[i] = unwrap(args.car(), parameterTypes[i]);
    if (!args.cdr().isNil()) {
      unwrapList(result, parameterTypes, (Pair) args.cdr(), i + 1);
    }
  }

  public static Object unwrap(ArcObject arcObject, Class javaType) {
    if (javaType != Object.class && javaType.isAssignableFrom(arcObject.getClass())) {
      return arcObject;
    } else {
      try {
        return convert(arcObject.unwrap(), javaType);
      } catch (ClassCastException e) {
        throw new ArcError("Can't convert " + arcObject.getClass().getName() + " - " + arcObject + " ( a " + arcObject.type() + ") to " + javaType, e);
      }
    }
  }

  private static Object convert(Object o, Class javaType) {
    if (o == Boolean.FALSE && !(javaType == Boolean.class || javaType == Boolean.TYPE)) {
      return null;
    } else if (javaType == Integer.class || javaType == Integer.TYPE) {
      return ((Long) o).intValue();
    } else if (javaType == Long.class || javaType == Long.TYPE || javaType == Double.class || javaType == Double.TYPE) {
      return o;
    } else if (javaType == Float.class || javaType == Float.TYPE) {
      return ((Double) o).floatValue();
    } else if (javaType == Boolean.class) {
      return o != Boolean.FALSE;
    } else if (javaType == Void.class) {
      return o == Boolean.FALSE ? null : o;
    } else {
      return o;
    }
  }

  private static Method findMethod(Class c, String methodName, int argCount) {
    for (int i = 0; i < c.getMethods().length; i++) {
      Method method = c.getMethods()[i];
      if (method.getName().equals(methodName) && method.getParameterTypes().length == argCount) {
        return method;
      }
    }
    throw new ArcError("no method " + methodName + " found on " + c + " with " + argCount + " parameters");
  }

  private static boolean match(Class[] parameterTypes, Pair args, int i) {
    return i == parameterTypes.length && args.isNil() || match(parameterTypes[i], args.car()) && match(parameterTypes, (Pair) args.cdr(), i + 1);
  }

  private static boolean match(Class parameterType, ArcObject arcObject) {
    if (parameterType == Boolean.class) {
      return true;
    } else if (isPrimitiveNumber(parameterType) && arcObject instanceof ArcNumber) {
      return true;
    } else if (Number.class.isAssignableFrom(parameterType) && arcObject instanceof ArcNumber) {
      return true;
    } else if (Character.class.isAssignableFrom(parameterType) && arcObject instanceof ArcCharacter) {
      return true;
    } else if (parameterType == String.class && (arcObject instanceof ArcString || arcObject instanceof Symbol)) {
      return true;
    } else if (parameterType == List.class && arcObject instanceof Pair) {
      return true;
    } else if (parameterType == Map.class && arcObject instanceof Hash) {
      return true;
    } else if (!parameterType.isPrimitive() && arcObject.isNil()) {
      return true;
    } else if (parameterType.isAssignableFrom(arcObject.unwrap().getClass())) {
      return true;
    }
    return false;
  }

  private static boolean isPrimitiveNumber(Class p) {
    return p == Integer.TYPE || p == Long.TYPE || p == Double.TYPE || p == Float.TYPE;
  }
  
  public static void main(String[] args) throws BadLocationException, IOException {
    JFrame jf = new JFrame();
    jf.setBounds(200, 200, 400, 400);
    jf.setTitle("testing");
    JTextPane jtp = new JTextPane();
    jtp.setContentType("text/html");
    
    jtp.setCaretColor(Color.white);
    HTMLDocument doc = (HTMLDocument) jtp.getDocument();
    doc.getStyleSheet().addRule(" .foo { color: red;} .bar {background:blue;color:white;}");
    jtp.setText("<html><body thing='what'><span id='parent'>parent blah<span id='the_foo' class='foo'>(</span> <span class='foo'>this is some bar text</span></span></body></html>");
    
    jtp.addCaretListener(new CaretListener() {
      public void caretUpdate(CaretEvent event) {
      }
    });
    jf.getContentPane().setLayout(new BoxLayout(jf.getContentPane(), BoxLayout.Y_AXIS));
    jf.add(fileControl());
    jf.add(new JScrollPane(jtp));
    jf.show();
    
    jtp.grabFocus();
    jtp.getCaret().setDot(20);
    jtp.getCaret().moveDot(30);
    Element[] elements = doc.getRootElements();
    for (int i = 0; i < elements.length; i++) {
      Element element = elements[i];
      System.out.println("element " + i);
      showInfo(element);
    }

    System.out.println(jtp.getText(0, jtp.getDocument().getEndPosition().getOffset()));
    
    Element fooElement = doc.getCharacterElement(12);
    doc.setOuterHTML(fooElement, "<span class='bar'>(</span>");
  }

  private static void showInfo(Element element) {
    System.out.println("Element " + element + "attrs " + attrs(element));
    for (int i = 0; i < element.getElementCount(); i++) {
      Element k = element.getElement(i);
      showInfo(k);
    }
  }

  private static String attrs(Element element) {
    StringBuffer sb = new StringBuffer();
    AttributeSet attributes = element.getAttributes();
    for (Enumeration e = attributes.getAttributeNames(); e.hasMoreElements(); ) {
      Object name = e.nextElement();
      Object value = attributes.getAttribute(name);
      sb.append(name + "  " + name.getClass().getName() + "\t=\t" + value + " " + value.getClass().getName() + "\n");
    }
    return sb.toString();
  }

  private static Box fileControl() {
    Box box = Box.createHorizontalBox();
    JTextField filename = new JTextField();
    Dimension d = filename.getMaximumSize();
    Dimension min = filename.getMinimumSize();
    filename.setMaximumSize(new Dimension((int) d.getWidth(), (int) min.getHeight()));
    box.add(filename);
    JButton jButton = new JButton("Open");
    box.add(jButton);
    box.add(new JButton("Save"));
    return box;
  }

  private static String text() {
    String foo = "vd lfvgs fjlvkergel kjvflkjelrkj\n erfelgk lkrg elrkgj k\n erglwekrg lmvlkemrlkwemrlkgmwl ml\n";
    for (int i = 0; i < 8; i++) {
      foo += foo;
    }
    return foo;
  }
}