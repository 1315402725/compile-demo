package com.demo.products.classloader;

import com.sun.tools.javac.file.BaseFileObject;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import org.springframework.boot.loader.jar.JarFile;
import org.springframework.boot.system.ApplicationHome;

import javax.tools.DiagnosticListener;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.stream.Collectors;

/**
 * 把一段Java字符串变成类
 */
public class MemoryClassLoader extends URLClassLoader {


    private static final Map<String, byte[]> classBytes = new ConcurrentHashMap<>();


    private MemoryClassLoader() {
        super(new URL[0], MemoryClassLoader.class.getClassLoader());
    }
    private static final Map<String, MemoryClassLoader> CLASSLOADER_MAP = new ConcurrentHashMap<String,MemoryClassLoader>(){{
        put(KEY_CLASSLOADER,new MemoryClassLoader());
    }};

    private static final String KEY_CLASSLOADER = "key_classloader" ;

    /**
     * 获取实例
     * @return
     */
    public static MemoryClassLoader getInstance(){
        return CLASSLOADER_MAP.get(KEY_CLASSLOADER);
    }

    /**
     * 生成新的实例
     * @return
     */
    public static MemoryClassLoader genInstance(){
        MemoryClassLoader classLoader = new MemoryClassLoader();
        CLASSLOADER_MAP.put(KEY_CLASSLOADER,new MemoryClassLoader());
        return classLoader;
    }


    /**
     * 注册Java 字符串到内存类加载器中
     *
     * @param className 类名字
     * @param javaStr   Java字符串
     */
    public void registerJava(String className, String javaStr) {
        try {
            this.classBytes.putAll(compile(className, javaStr));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * 自定义Java文件管理器
     *
     * @param var1
     * @param var2
     * @param var3
     * @return
     */
    public static SpringJavaFileManager getStandardFileManager(DiagnosticListener<? super JavaFileObject> var1, Locale var2, Charset var3) {
        Context var4 = new Context();
        var4.put(Locale.class, var2);
        if (var1 != null) {
            var4.put(DiagnosticListener.class, var1);
        }

        PrintWriter var5 = var3 == null ? new PrintWriter(System.err, true) : new PrintWriter(new OutputStreamWriter(System.err, var3), true);
        var4.put(Log.outKey, var5);
        return new SpringJavaFileManager(var4, true, var3);
    }

    /**
     * 编译Java代码
     *
     * @param className 类名字
     * @param javaStr   Java代码
     * @return class 二进制
     */
    private static Map<String, byte[]> compile(String className, String javaStr) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager stdManager = getStandardFileManager(null, null, null);
        try (MemoryJavaFileManager manager = new MemoryJavaFileManager(stdManager)) {
            JavaFileObject javaFileObject = manager.makeStringSource(className, javaStr);
            JavaCompiler.CompilationTask task = compiler.getTask(null, manager, null, null, null, Arrays.asList(javaFileObject));
            Boolean result = task.call();
            if (result != null && result.booleanValue()) {
                return manager.getClassBytes();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] buf = classBytes.get(name);
        if (buf == null) {
            return super.findClass(name);
        }
        classBytes.remove(name);
        return defineClass(name, buf, 0, buf.length);
    }

    /**
     * 开放findClass 给外部使用
     *
     * @param name classname
     * @return class对象
     */
    public Class<?> getClass(String name) throws ClassNotFoundException {
        return this.findClass(name);
    }

    /**
     * 获取jar包所在路径
     *
     * @return jar包所在路径
     */
    public static String getPath() {
        ApplicationHome home = new ApplicationHome(MemoryJavaFileManager.class);
        String path = home.getSource().getPath();
        return path;
    }

    /**
     * 判断是否jar模式运行
     *
     * @return
     */
    public static boolean isJar() {
        return getPath().endsWith(".jar");
    }

}


/**
 * 内存Java文件管理器
 * 用于加载springboot boot info lib 下面的依赖资源
 */
class MemoryJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {

    // compiled classes in bytes:
    final Map<String, byte[]> classBytes = new HashMap<String, byte[]>();

    final Map<String, List<JavaFileObject>> classObjectPackageMap = new HashMap<>();

    private JavacFileManager javaFileManager;

    /**
     * key 包名 value javaobj 主要给jdk编译class的时候找依赖class用
     */
    public final static Map<String, List<JavaFileObject>> CLASS_OBJECT_PACKAGE_MAP = new HashMap<>();

    private static final Object lock = new Object();

    private boolean isInit = false;


    public void init(){
        try {
            String jarBaseFile = MemoryClassLoader.getPath();
            JarFile jarFile = new JarFile(new File(jarBaseFile));
            List<JarEntry> entries = jarFile.stream().filter(jarEntry -> {
                return jarEntry.getName().endsWith(".jar");
            }).collect(Collectors.toList());
            JarFile libTempJarFile = null;
            List<JavaFileObject> onePackgeJavaFiles =  null;
            String packgeName = null;
            for (JarEntry entry : entries) {
                libTempJarFile = jarFile.getNestedJarFile(jarFile.getEntry(entry.getName()));
                if(libTempJarFile.getName().contains("tools.jar")){
                    continue;
                }
                Enumeration<JarEntry> tempEntriesEnum = libTempJarFile.entries();
                while (tempEntriesEnum.hasMoreElements()) {
                    JarEntry jarEntry = tempEntriesEnum.nextElement();
                    String classPath = jarEntry.getName().replace("/", ".");
                    if (!classPath.endsWith(".class") || jarEntry.getName().lastIndexOf("/") == -1) {
                        continue;
                    } else {
                        packgeName = classPath.substring(0, jarEntry.getName().lastIndexOf("/"));
                        onePackgeJavaFiles = CLASS_OBJECT_PACKAGE_MAP.containsKey(packgeName) ? CLASS_OBJECT_PACKAGE_MAP.get(packgeName) :  new ArrayList<>();
                        onePackgeJavaFiles.add(new MemorySpringBootInfoJavaClassObject(jarEntry.getName().replace("/", ".").replace(".class", ""),
                                new URL(libTempJarFile.getUrl(), jarEntry.getName()), javaFileManager));
                        CLASS_OBJECT_PACKAGE_MAP.put(packgeName,onePackgeJavaFiles);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        isInit = true;

    }



    MemoryJavaFileManager(JavaFileManager fileManager) {
        super(fileManager);
        this.javaFileManager = (JavacFileManager)fileManager;
    }

    public Map<String, byte[]> getClassBytes() {
        return new HashMap<String, byte[]>(this.classBytes);
    }

    @Override
    public void flush() throws IOException {
    }

    @Override
    public void close() throws IOException {
        classBytes.clear();
    }


    public List<JavaFileObject> getLibJarsOptions(String packgeName) {
        synchronized (lock){
            if(!isInit){
                init();
            }
        }
        return CLASS_OBJECT_PACKAGE_MAP.get(packgeName);
    }

    @Override
    public Iterable<JavaFileObject> list(Location location,
                                         String packageName,
                                         Set<Kind> kinds,
                                         boolean recurse)
            throws IOException {


        if ("CLASS_PATH".equals(location.getName()) && MemoryClassLoader.isJar()) {
            List<JavaFileObject> result =  getLibJarsOptions(packageName);
            if(result!=null){
                return result;
            }
        }

        Iterable<JavaFileObject> it = super.list(location, packageName, kinds, recurse);

        if (kinds.contains(Kind.CLASS)) {
            final List<JavaFileObject> javaFileObjectList = classObjectPackageMap.get(packageName);
            if (javaFileObjectList != null) {
                if (it != null) {
                    for (JavaFileObject javaFileObject : it) {
                        javaFileObjectList.add(javaFileObject);
                    }
                }
                return javaFileObjectList;
            } else {
                return it;
            }
        } else {
            return it;
        }
    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject file) {
        if (file instanceof MemoryInputJavaClassObject) {
            return ((MemoryInputJavaClassObject) file).inferBinaryName();
        }
        return super.inferBinaryName(location, file);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location, String className, Kind kind,
                                               FileObject sibling) throws IOException {
        if (kind == Kind.CLASS) {
            return new MemoryOutputJavaClassObject(className);
        } else {
            return super.getJavaFileForOutput(location, className, kind, sibling);
        }
    }

    JavaFileObject makeStringSource(String className, final String code) {
        String classPath = className.replace('.', '/') + Kind.SOURCE.extension;

        return new SimpleJavaFileObject(URI.create("string:///" + classPath), Kind.SOURCE) {
            @Override
            public CharBuffer getCharContent(boolean ignoreEncodingErrors) {
                return CharBuffer.wrap(code);
            }
        };
    }

    void makeBinaryClass(String className, final byte[] bs) {
        JavaFileObject javaFileObject = new MemoryInputJavaClassObject(className, bs);

        String packageName = "";
        int pos = className.lastIndexOf('.');
        if (pos > 0) {
            packageName = className.substring(0, pos);
        }
        List<JavaFileObject> javaFileObjectList = classObjectPackageMap.get(packageName);
        if (javaFileObjectList == null) {
            javaFileObjectList = new LinkedList<>();
            javaFileObjectList.add(javaFileObject);

            classObjectPackageMap.put(packageName, javaFileObjectList);
        } else {
            javaFileObjectList.add(javaFileObject);
        }
    }

    class MemoryInputJavaClassObject extends SimpleJavaFileObject {
        final String className;
        final byte[] bs;

        MemoryInputJavaClassObject(String className, byte[] bs) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
            this.className = className;
            this.bs = bs;
        }

        @Override
        public InputStream openInputStream() {
            return new ByteArrayInputStream(bs);
        }

        public String inferBinaryName() {
            return className;
        }
    }


    class MemoryOutputJavaClassObject extends SimpleJavaFileObject {
        final String className;

        MemoryOutputJavaClassObject(String className) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
            this.className = className;
        }

        @Override
        public OutputStream openOutputStream() {
            return new FilterOutputStream(new ByteArrayOutputStream()) {
                @Override
                public void close() throws IOException {
                    out.close();
                    ByteArrayOutputStream bos = (ByteArrayOutputStream) out;
                    byte[] bs = bos.toByteArray();
                    classBytes.put(className, bs);
                    makeBinaryClass(className, bs);
                }
            };
        }
    }
}

/**
 * 用来读取springboot的class
 */
class MemorySpringBootInfoJavaClassObject extends BaseFileObject {
    private final String className;
    private URL url;

    MemorySpringBootInfoJavaClassObject(String className, URL url, JavacFileManager javacFileManager) {
        super(javacFileManager);
        this.className = className;
        this.url = url;
    }

    @Override
    public Kind getKind() {
        return JavaFileObject.Kind.valueOf("CLASS");
    }

    @Override
    public URI toUri() {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String getName() {
        return className;
    }

    @Override
    public InputStream openInputStream() {
        try {
            return url.openStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
        return null;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
        return null;
    }

    @Override
    public Writer openWriter() throws IOException {
        return null;
    }

    @Override
    public long getLastModified() {
        return 0;
    }

    @Override
    public boolean delete() {
        return false;
    }

    public String inferBinaryName() {
        return className;
    }

    @Override
    public String getShortName() {
        return className.substring(className.lastIndexOf("."));
    }

    @Override
    protected String inferBinaryName(Iterable<? extends File> iterable) {
        return className;
    }


    @Override
    public boolean equals(Object o) {
        return false;
    }

    @Override
    public int hashCode() {
        return 0;
    }


    @Override
    public boolean isNameCompatible(String simpleName, Kind kind) {
        return false;
    }
}

/**
 * java 文件管理器 主要用来 重新定义class loader
 */
class SpringJavaFileManager extends JavacFileManager {


    public SpringJavaFileManager(Context context, boolean b, Charset charset) {
        super(context, b, charset);
    }


    @Override
    public ClassLoader getClassLoader(Location location) {
        nullCheck(location);
        Iterable var2 = this.getLocation(location);
        if (var2 == null) {
            return null;
        } else {
            ListBuffer var3 = new ListBuffer();
            Iterator var4 = var2.iterator();

            while (var4.hasNext()) {
                File var5 = (File) var4.next();

                try {
                    var3.append(var5.toURI().toURL());
                } catch (MalformedURLException var7) {
                    throw new AssertionError(var7);
                }
            }
            return this.getClassLoader((URL[]) var3.toArray(new URL[var3.size()]));
        }
    }

    protected ClassLoader getClassLoader(URL[] var1) {
        ClassLoader var2 = this.getClass().getClassLoader();
        try {
            Class loaderClass = Class.forName("org.springframework.boot.loader.LaunchedURLClassLoader");
            Class[] var4 = new Class[]{URL[].class, ClassLoader.class};
            Constructor var5 = loaderClass.getConstructor(var4);
            return (ClassLoader) var5.newInstance(var1, var2);
        } catch (Throwable var6) {
        }
        return new URLClassLoader(var1, var2);
    }


}