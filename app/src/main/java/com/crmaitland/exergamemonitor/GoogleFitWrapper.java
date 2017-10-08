package com.crmaitland.exergamemonitor;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.gms.common.api.GoogleApiClient;

/**
 * Created by Cameron on 10/7/2017.
 */

public class GoogleFitWrapper implements Parcelable {
    public GoogleApiClient mClient;

    GoogleFitWrapper(GoogleApiClient apiClient){
        this.mClient = apiClient;
    }

    protected GoogleFitWrapper(Parcel in) {
        mClient = (GoogleApiClient) in.readValue(GoogleApiClient.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(mClient);
    }

    @SuppressWarnings("unused")
    public static final Parcelable.Creator<GoogleFitWrapper> CREATOR = new Parcelable.Creator<GoogleFitWrapper>() {
        @Override
        public GoogleFitWrapper createFromParcel(Parcel in) {
            return new GoogleFitWrapper(in);
        }

        @Override
        public GoogleFitWrapper[] newArray(int size) {
            return new GoogleFitWrapper[size];
        }
    };
}
