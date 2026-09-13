package io.aetheris.orchestrator.model;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class ModelArenaService {
    private final ModelArenaMeasurementRepository repository; public ModelArenaService(ModelArenaMeasurementRepository repository){this.repository=repository;}
    public ModelArenaMeasurementEntity record(ModelArenaRecordRequest r){if(r.qualityScore()!=null&&(r.qualityScore()<0||r.qualityScore()>100))throw new IllegalArgumentException("Quality score must be between 0 and 100");return repository.save(new ModelArenaMeasurementEntity(UUID.randomUUID(),r.providerId(),r.model()==null?"":r.model(),r.scenario()==null?"runtime":r.scenario(),r.success(),r.latencyMs(),r.qualityScore(),r.detail()));}
    public void runtime(String providerId,String model,boolean success,long latencyMs,String detail){record(new ModelArenaRecordRequest(providerId,model,"runtime",success,latencyMs,null,detail));}
    public List<ModelArenaMeasurementEntity> recent(){return repository.findTop100ByOrderByCreatedAtDesc();}
}
