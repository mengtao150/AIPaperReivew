package com.reviewer.scout.controller;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.reviewer.scout.entity.Article;
import com.reviewer.scout.service.EvidenceService;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/scans/{id}/evidence")
public class EvidenceController {
    private final EvidenceService service;
    public EvidenceController(EvidenceService service){this.service=service;}
    @GetMapping public ObjectNode read(@RequestAttribute("workspace")String workspace,@PathVariable Long id){return service.read(workspace,id);}
    public record AnalysisRequest(long revision,int limit){}
    @PostMapping("/analyze") public ObjectNode analyze(@RequestAttribute("workspace")String workspace,@PathVariable Long id,@RequestBody AnalysisRequest request){return service.start(workspace,id,request.revision(),request.limit());}
    @GetMapping("/job") public ObjectNode job(@RequestAttribute("workspace")String workspace,@PathVariable Long id){return service.job(workspace,id);}
    @PatchMapping public ObjectNode edit(@RequestAttribute("workspace")String workspace,@PathVariable Long id,@RequestBody ObjectNode body){return service.update(workspace,id,body);}
    @GetMapping("/articles/{pmid}") public Article article(@PathVariable Long id,@PathVariable String pmid){return service.article(id,pmid);}
}
