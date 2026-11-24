package com.dir.sync.model;

import org.json.JSONObject;

public class BapiContext {

    private final String       chronicleId;
    private final JSONObject   boIdsJson;
    private final DirOperation operation;
    private final int          sno;
    private final String       dirNumber;
    private final String       planningPlant;
    private final String       title;

    public BapiContext(String chronicleId, JSONObject boIdsJson, DirOperation operation,
                       int sno, String dirNumber, String planningPlant, String title) {
        this.chronicleId   = chronicleId;
        this.boIdsJson     = boIdsJson;
        this.operation     = operation;
        this.sno           = sno;
        this.dirNumber     = dirNumber;
        this.planningPlant = planningPlant;
        this.title         = title;
    }

    public String       getChronicleId()  { return chronicleId;   }
    public JSONObject   getBoIdsJson()    { return boIdsJson;     }
    public DirOperation getOperation()   { return operation;     }
    public int          getSno()          { return sno;           }
    public String       getDirNumber()    { return dirNumber;     }
    public String       getPlanningPlant(){ return planningPlant; }
    public String       getTitle()        { return title;         }
}
