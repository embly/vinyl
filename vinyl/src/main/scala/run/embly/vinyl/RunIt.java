package run.embly.vinyl;

import com.apple.foundationdb.record.provider.foundationdb.FDBDatabase;
import com.apple.foundationdb.record.provider.foundationdb.FDBRecordContext;
import vinyl.transport.Response;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class RunIt {

    public static Response run(FDBDatabase db, Function<FDBRecordContext, Response> runnable) {
        AtomicReference<Response> resp = new AtomicReference<>(Response.defaultInstance());
        db.run(context -> {
            resp.set(runnable.apply(context));
            return null;
        });
        return resp.get();
    }

}
