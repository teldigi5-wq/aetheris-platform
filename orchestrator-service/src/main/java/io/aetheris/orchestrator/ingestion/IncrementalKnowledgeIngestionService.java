package io.aetheris.orchestrator.ingestion;

import io.aetheris.orchestrator.memory.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class IncrementalKnowledgeIngestionService {
    public static final String TOMBSTONE_TAG="stage9-tombstoned";
    private final IngestionSourceStateRepository states; private final KnowledgeIngestionService ingestion; private final SemanticMemoryService memory;
    public IncrementalKnowledgeIngestionService(IngestionSourceStateRepository states,KnowledgeIngestionService ingestion,SemanticMemoryService memory){this.states=states;this.ingestion=ingestion;this.memory=memory;}
    @Transactional public IncrementalIngestionResult sync(IngestKnowledgeRequest request){String kind=require(request.sourceKind(),"source kind").toUpperCase(Locale.ROOT);String sourceId=require(request.sourceId(),"source id");String namespace=request.namespace()==null||request.namespace().isBlank()?kind.toLowerCase(Locale.ROOT):request.namespace().trim();String hash=sha256(request.content()==null?"":request.content());IngestionSourceStateEntity state=states.findBySourceKindAndSourceIdAndNamespace(kind,sourceId,namespace).orElseGet(()->new IngestionSourceStateEntity(UUID.randomUUID(),kind,sourceId,namespace,request.scope()));if(!state.isTombstoned()&&hash.equals(state.getContentHash()))return result(state,false,"Source hash unchanged; no re-ingestion performed");markNodesTombstoned(state.nodeIds());IngestKnowledgeRequest normalized=new IngestKnowledgeRequest(kind,sourceId,request.title(),request.content(),request.scope(),namespace,request.protectedData(),request.tags());KnowledgeIngestionResult ingested=ingestion.ingest(normalized);state.activate(hash,request.scope(),ingested.nodeIds());states.save(state);return result(state,true,"Source changed; active chunks refreshed and superseded chunks tombstoned");}
    @Transactional public IncrementalIngestionResult tombstone(String kind,String sourceId,String namespace){String k=require(kind,"source kind").toUpperCase(Locale.ROOT),s=require(sourceId,"source id"),n=require(namespace,"namespace");IngestionSourceStateEntity state=states.findBySourceKindAndSourceIdAndNamespace(k,s,n).orElseThrow(()->new NoSuchElementException("Unknown ingestion source"));markNodesTombstoned(state.nodeIds());state.tombstone();states.save(state);return result(state,true,"Source tombstoned; superseded knowledge is excluded from Stage 9 searches");}
    public List<IngestionSourceStateEntity> recent(){return states.findTop200ByOrderByUpdatedAtDesc();}
    private void markNodesTombstoned(List<UUID> ids){for(UUID id:ids){KnowledgeNodeEntity node;try{node=memory.required(id);}catch(Exception e){continue;}Set<String> tags=new LinkedHashSet<>(node.getTags());tags.add(TOMBSTONE_TAG);memory.upsert(new UpsertKnowledgeRequest(node.getScope(),node.getNamespace(),node.getMemoryKey(),node.getContent(),tags,node.isProtectedData()));}}
    private IncrementalIngestionResult result(IngestionSourceStateEntity s,boolean changed,String detail){return new IncrementalIngestionResult(s.getSourceKind(),s.getSourceId(),s.getNamespace(),s.getRevision(),changed,s.isTombstoned(),s.getChunkCount(),s.nodeIds(),detail);}
    private String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String require(String v,String label){if(v==null||v.isBlank())throw new IllegalArgumentException(label+" is required");return v.trim();}
}
