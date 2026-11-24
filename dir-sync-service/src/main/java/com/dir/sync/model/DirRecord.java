package com.dir.sync.model;

import org.json.JSONObject;

public class DirRecord {

    private final int         sno;
    private final String      chronicleId;
    private final DirOperation operation;
    private final JSONObject  boIdsJson;
    private final String      dirNumber;
    private final String      planningPlant;
    private final String      title;
    private final String      buName;

    public DirRecord(int sno, String chronicleId, DirOperation operation,
                     JSONObject boIdsJson, String dirNumber,
                     String planningPlant, String title, String buName) {
        this.sno          = sno;
        this.chronicleId  = chronicleId;
        this.operation    = operation;
        this.boIdsJson    = boIdsJson;
        this.dirNumber    = dirNumber;
        this.planningPlant = planningPlant;
        this.title        = title;
        this.buName       = buName;
    }

    public int         getSno()          { return sno;          }
    public String      getChronicleId()  { return chronicleId;  }
    public DirOperation getOperation()   { return operation;    }
    public JSONObject  getBoIdsJson()    { return boIdsJson;    }
    public String      getDirNumber()    { return dirNumber;    }
    public String      getPlanningPlant(){ return planningPlant;}
    public String      getTitle()        { return title;        }
    public String      getBuName()       { return buName;       }
}
