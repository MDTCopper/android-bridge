package copper.bridge.v1;

import arc.Files.*;
import arc.files.*;
import arc.util.*;

import java.io.*;

/**
 * The {@link Fi} this branch's {@link BridgeFiles} hands out, mirroring arc's own desktop backend, which overrides
 * only what its platform answers differently. Reading an {@code internal} or {@code classpath} path, its existence
 * and its length are all answered by arc's base class, which falls back to the class loader that owns the jars, and
 * a directory that lives only inside a jar has no listing on the desktop either. What is answered differently here
 * is {@code local}, which arc rebases for {@code external} only, and the handle type, which has to stay this one so
 * the rebasing survives a {@code child}/{@code parent} walk.
 */
public class BridgeFi extends Fi{
    private final BridgeFiles files;

    BridgeFi(BridgeFiles files, String fileName, FileType type){
        super(fileName.replace('\\', '/'), type);
        this.files = files;
    }

    /** Fi(File, FileType) is protected, so only a subclass may call it - which this is. */
    BridgeFi(BridgeFiles files, File file, FileType type){
        super(file, type);
        this.files = files;
    }

    @Override
    public Fi child(String name){
        name = name.replace('\\', '/');
        if(file.getPath().length() == 0) return new BridgeFi(files, new File(name), type);
        return new BridgeFi(files, new File(file, name), type);
    }

    @Override
    public Fi sibling(String name){
        name = name.replace('\\', '/');
        if(file.getPath().length() == 0) throw new ArcRuntimeException("Cannot get the sibling of the root.");
        return new BridgeFi(files, new File(file.getParent(), name), type);
    }

    @Override
    public Fi parent(){
        File parent = file.getParentFile();
        if(parent == null){
            parent = type == FileType.absolute ? new File("/") : new File("");
        }
        return new BridgeFi(files, parent, type);
    }

    @Override
    public File file(){
        if(type == FileType.local) return new File(files.getLocalStoragePath(), file.getPath());
        return super.file();
    }
}
