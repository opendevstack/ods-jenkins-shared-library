package org.ods.orchestration.util

import com.cloudbees.groovy.cps.NonCPS

class DocumentHistoryEntry implements Map, Serializable {

    private final Long entryId
    private final Map delegate
    private final String projectVersion
    private final String previousProjectVersion
    String rational

    DocumentHistoryEntry(Map map, Long entryId, String projectVersion,
                         String previousProjectVersion, String rational) {
        this.delegate = new HashMap(map)
        this.entryId = entryId
        this.projectVersion = projectVersion
        this.previousProjectVersion = previousProjectVersion
        this.rational = rational
    }

    @NonCPS
    @Override
    int size() {
        return delegate.size()
    }

    @NonCPS
    @Override
    boolean isEmpty() {
        return delegate.isEmpty()
    }

    @NonCPS
    @Override
    boolean containsKey(Object key) {
        return delegate.containsKey(key)
    }

    @NonCPS
    @Override
    boolean containsValue(Object value) {
        return delegate.containsValue(value)
    }

    @NonCPS
    @Override
    Object get(Object key) {
        return delegate.get(key)
    }

    @NonCPS
    @Override
    Object put(Object key, Object value) {
        return delegate.put(key, value)
    }

    @NonCPS
    @Override
    Object remove(Object key) {
        return delegate.remove(key)
    }

    @NonCPS
    @Override
    void putAll(Map m) {
        delegate.putAll(m)
    }

    @NonCPS
    @Override
    void clear() {
        delegate.clear()
    }

    @NonCPS
    @Override
    Set keySet() {
        return (delegate + [
            id: entryId,
            projectVersion: projectVersion,
            previousProjectVersion: previousProjectVersion,
            rational: rational,
        ]).keySet()
    }

    @NonCPS
    @Override
    Collection values() {
        return (delegate + [
            entryId: entryId,
            projectVersion: projectVersion,
            previousProjectVersion: previousProjectVersion,
            rational: rational,
        ]).values()
    }

    @NonCPS
    @Override
    Set<Entry> entrySet() {
        return (delegate + [
            entryId: entryId,
            projectVersion: projectVersion,
            previousProjectVersion: previousProjectVersion,
            rational: rational,
        ]).entrySet()
    }

    @NonCPS
    Long getEntryId() {
        return entryId
    }

    @NonCPS
    String getRational() {
        return rational
    }

    @NonCPS
    String getProjectVersion() {
        return projectVersion
    }

    @NonCPS
    String getPreviousProjectVersion() {
        return previousProjectVersion
    }

    @NonCPS
    Map getDelegate() {
        return delegate
    }

    @NonCPS
    DocumentHistoryEntry cloneIt() {
        def bos = new ByteArrayOutputStream()
        def os = new ObjectOutputStream(bos)
        os.writeObject(this.delegate)
        def ois = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))

        def newDelegate = ois.readObject()
        DocumentHistoryEntry result = new DocumentHistoryEntry(newDelegate,
            entryId, projectVersion, previousProjectVersion)
        result.rational = this.rational
        return result
    }

}
