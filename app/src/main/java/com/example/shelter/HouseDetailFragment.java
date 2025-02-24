package com.example.shelter;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import com.example.shelter.data.SessionManager;
import com.example.shelter.data.ShelterDBContract.RatingEntry;
import com.example.shelter.data.ShelterDBContract.HouseTypeEntry;
import com.example.shelter.data.ShelterDBContract.HouseEntry;
import com.example.shelter.data.ShelterDBContract.AlertEntry;
import com.example.shelter.data.ShelterDBHelper;
import com.example.shelter.network.ImageRequester;
import com.example.shelter.adapter.ImageSliderAdapter;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.smarteist.autoimageslider.IndicatorView.animation.type.IndicatorAnimationType;
import com.smarteist.autoimageslider.SliderAnimations;
import com.smarteist.autoimageslider.SliderView;

public class HouseDetailFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
    static final public String TAG = HouseDetailFragment.class.getSimpleName();
    private static final int HOUSE_DETAIL_LOADER = 901;
    private static final int IS_FAVOURITE_LOADER = 121;
    private static final int CHECK_CONTACT_SENT = 131;

    static public final String KEY_HOUSE_URI = "houseUri";


   private Activity mActivity;
    private Context mContext;

    private Uri mHouseUri;
    private boolean moreInfoTVIsExpanded = false;
    private boolean isFavourite;
    private int checkContact = -1;

    //All text view in this fragment
    private TextView houseNameTV;
    private TextView houseTypeTV;
    private TextView rentCostTV;
    private TextView houseAddressTV;
    private TextView houseAddressLabelTV;
    private TextView nearPointDistanceTV;
    private TextView salePriceTV;
    private TextView moreInfoTV;
    private TextView moreInfoTVLabel;
    private TextView houseAreaTV;
    private ImageButton isFavouriteButton;
    private MaterialButton sendContactButton;
    private ImageButton alertButton;
    private TextView countViewsTV;

    private ImageView closeHouseIcon;
    private SliderView sliderView;
    private ImageSliderAdapter imageSliderAdapter;
    private ImageRequester imageRequester;

    private Cursor cursorIsFavourite = null;
    private SessionManager sessionManager;

    
    //Value for choice items
    private String[] chooseItems;
    private int checkItem;
    
    //House location
    private LatLng houseLatLng;

    public static Fragment NewInstance(String houseUri) {
        Bundle deliver = new Bundle();
        deliver.putString(KEY_HOUSE_URI, houseUri);
        Fragment newFragment = new HouseDetailFragment();
        newFragment.setArguments(deliver);
        return newFragment;
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = getContext();
        mActivity = getActivity();
        //Init session
        sessionManager = new SessionManager(mContext);
        //Init Image Requester
        imageRequester = new ImageRequester(mContext);
        
        //Get argument from deliver
        if (getArguments() != null) {
            mHouseUri = Uri.parse(getArguments().getString(KEY_HOUSE_URI, null));
        }

        //Choosing items for alert house
        chooseItems = AlertEntry.CREATE_TOPIC_SELECT_ITEMS(mContext);
        checkItem = 0;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.house_detail_fragment, container, false);
        Log.d(TAG, "House Uri: " + mHouseUri);

        //Find all needed view to display data
        houseNameTV = view.findViewById(R.id.house_name);
        houseTypeTV = view.findViewById(R.id.house_type);
        rentCostTV =  view.findViewById(R.id.rent_price);
        houseAddressTV =  view.findViewById(R.id.house_address);
        houseAddressLabelTV = view.findViewById(R.id.house_address_label);
        nearPointDistanceTV =  view.findViewById(R.id.near_point_distance);
        salePriceTV =  view.findViewById(R.id.sale_price);
        sendContactButton =  view.findViewById(R.id.send_contact_button);
        moreInfoTV =  view.findViewById(R.id.more_detail);
        moreInfoTVLabel =  view.findViewById(R.id.mote_detail_label);
        houseAreaTV =  view.findViewById(R.id.house_area);
        countViewsTV = view.findViewById(R.id.count_views);

        //Button
        sendContactButton = view.findViewById(R.id.send_contact_button);
        sendContactButton.setText(R.string.send_contact);

        isFavouriteButton =  view.findViewById(R.id.favourite);
        isFavouriteButton.setTag(R.drawable.outline_favorite_border_24);
        isFavouriteButton.setImageResource(R.drawable.outline_favorite_border_24);
        isFavourite = false;

        alertButton = view.findViewById(R.id.report_house);

        //Icon
        closeHouseIcon = view.findViewById(R.id.house_close);

        //Set image slider
        sliderView = view.findViewById(R.id.image_slider);
        sliderView.setIndicatorAnimation(IndicatorAnimationType.WORM);
        sliderView.setSliderTransformAnimation(SliderAnimations.SIMPLETRANSFORMATION);


        //Set on click listener for more info TV, for expandable
        moreInfoTVLabel.setOnClickListener(v -> {
            if (!moreInfoTVIsExpanded) {
                moreInfoTV.setMaxLines(40);
                moreInfoTVLabel.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.outline_expand_less_24, 0);
                moreInfoTVIsExpanded = true;
            } else {
                moreInfoTV.setMaxLines(0);
                moreInfoTVLabel.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.outline_expand_more_24, 0);
                moreInfoTVIsExpanded = false;
            }

        });

        //Set on click listener for address text view to display current location of the house on google map
        houseAddressLabelTV.setOnClickListener(v -> {
            Fragment mapFragment = MapsFragment.NewInstance(HouseDetailFragment.TAG, houseLatLng.latitude, houseLatLng.longitude);
            ((NavigationHost) mActivity).navigateTo(mapFragment, true);
        });


        //Favourite button
        isFavouriteButton.setOnClickListener(v -> {
            if (isFavourite) {
                isFavourite = false;
                isFavouriteButton.setImageResource(R.drawable.outline_favorite_border_24);
            } else {
                isFavourite = true;
                isFavouriteButton.setImageResource(R.drawable.outline_favorite_24);
            }
        });

        //Send contact button
        sendContactButton.setOnClickListener(v -> {
            Long userId = ContentUris.parseId(sessionManager.getUserUri());
            Long houseId = ContentUris.parseId(mHouseUri);
            ContentValues values = new ContentValues();
            values.put(RatingEntry.COLUMN_USER_ID, userId);
            values.put(RatingEntry.COLUMN_HOUSE_ID, houseId);

            if (checkContact == -1) {
                values.put(RatingEntry.COLUMN_STARS, RatingEntry.SEND_CONTACT);
                mContext.getContentResolver().insert(RatingEntry.CONTENT_URI, values);
                sendContactButton.setText(R.string.contact_sent);
                sendContactButton.setBackgroundColor(getResources().getColor(R.color.colorAccent, null));
                checkContact = RatingEntry.SEND_CONTACT;
                Toast.makeText(mContext, R.string.send_contact_buton_press, Toast.LENGTH_LONG).show();
            } else if (checkContact == RatingEntry.SEND_CONTACT || checkContact == RatingEntry.CONTACT_SOLVED){
                Toast.makeText(mContext, R.string.contact_sent_press, Toast.LENGTH_SHORT).show();
            } else if (checkContact == RatingEntry.HOUSE_OWNER) {
                Toast.makeText(mContext, R.string.contact_alert_house_owner, Toast.LENGTH_LONG).show();
            }

        });

        //Alert Button
        alertButton.setOnClickListener(v ->
                new MaterialAlertDialogBuilder(mContext)
                .setTitle(R.string.reporting_house)
                .setNeutralButton(R.string.cancel, (dialog, which) -> {

                })
                .setSingleChoiceItems(chooseItems, 0, (dialog, which) -> checkItem = which)
                .setPositiveButton(R.string.report, (dialog, which) -> {
                    Log.d(TAG, "onCreateView: " + checkItem);
                    ContentValues values = new ContentValues();
                    values.put(AlertEntry.COLUMN_ALERT_TOPIC_SELECT, checkItem);
                    values.put(AlertEntry.COLUMN_CONTENT, AlertEntry.GET_TOPIC_SELECT_ITEM(checkItem, mContext));
                    values.put(AlertEntry.COLUMN_HOUSE_ID, ContentUris.parseId(mHouseUri));
                    values.put(AlertEntry.COLUMN_ALERT_STATE, AlertEntry.UNSOLVED);
                    mContext.getContentResolver().insert(AlertEntry.CONTENT_URI, values);
                    Toast.makeText(mContext, R.string.report_house_sucess, Toast.LENGTH_SHORT).show();
                })
                .show());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Kick off the loader
        LoaderManager.getInstance(this).initLoader(HOUSE_DETAIL_LOADER, null, this);
        LoaderManager.getInstance(this).initLoader(IS_FAVOURITE_LOADER, null, this);
        LoaderManager.getInstance(this).initLoader(CHECK_CONTACT_SENT, null, this);
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
        // Define a projection that specifies the columns from the table we care about.
        String[] projection;
        CursorLoader cursorLoader = null;
        String selection;
        String[] selectionArgs;
        long userId = ContentUris.parseId(sessionManager.getUserUri());
        long houseId = ContentUris.parseId(mHouseUri);
        switch (id) {
            case HOUSE_DETAIL_LOADER:
                cursorLoader = new CursorLoader(mContext,   // Parent activity context
                        mHouseUri,   // Provider content URI to query
                        null,             // Columns to include in the resulting Cursor
                        null,                   // No selection clause
                        null,                   // No selection arguments
                        null);// Default sort order
                break;
            case IS_FAVOURITE_LOADER:
                projection = new String[]{
                        RatingEntry._ID,
                        RatingEntry.COLUMN_HOUSE_ID,
                        RatingEntry.COLUMN_USER_ID,
                        RatingEntry.COLUMN_STARS
                };
                selection = "user_id = ? AND stars = ? AND house_id = ?";
                selectionArgs = new String[]{Long.toString(userId), RatingEntry.FAVOURITE.toString(), Long.toString(houseId)};
                cursorLoader = new CursorLoader(mContext,   // Parent activity context
                        RatingEntry.CONTENT_URI,   // Provider content URI to query
                        projection,             // Columns to include in the resulting Cursor
                        selection,                   // No selection clause
                        selectionArgs,                   // No selection arguments
                        null);// Default sort order
                break;
            case CHECK_CONTACT_SENT:
                projection = new String[]{
                        RatingEntry._ID,
                        RatingEntry.COLUMN_HOUSE_ID,
                        RatingEntry.COLUMN_USER_ID,
                        RatingEntry.COLUMN_STARS
                };
                selection = "user_id = ? AND house_id = ?";
                selection += " AND (stars = ? OR stars = " + RatingEntry.CONTACT_SOLVED + " OR stars = " + RatingEntry.HOUSE_OWNER + ")";
                selectionArgs = new String[]{Long.toString(userId), Long.toString(houseId), RatingEntry.SEND_CONTACT.toString()};
                cursorLoader = new CursorLoader(mContext,   // Parent activity context
                        RatingEntry.CONTENT_URI,   // Provider content URI to query
                        projection,             // Columns to include in the resulting Cursor
                        selection,                   // No selection clause
                        selectionArgs,                   // No selection arguments
                        null);// Default sort order
                break;
            default:
                throw new IllegalArgumentException("Illegal Loader ID: Loader id: " + id);
        }


        // This loader will execute the ContentProvider's query method on a background thread
        return cursorLoader;
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
        // Proceed with moving to the first row of the cursor and reading data from it
        // (This should be the only row in the cursor)

        if (data.moveToFirst()) {
            switch (loader.getId()) {
                case HOUSE_DETAIL_LOADER:
                    String houseName = data.getString(data.getColumnIndex(HouseEntry.COLUMN_HOUSE_NAME));
                    String houseAddress = data.getString(data.getColumnIndex(HouseEntry.COLUMN_HOUSE_ADDRESS));
                    String houseRentCost = ShelterDBHelper.formatPrice(data.getFloat(data.getColumnIndex(HouseEntry.COLUMN_HOUSE_RENT_COST)), mContext);
                    String houseSalePrice = ShelterDBHelper.formatPrice(data.getFloat(data.getColumnIndex(HouseEntry.COLUMN_HOUSE_SALE_PRICE)), mContext);
                    String houseArea = data.getString(data.getColumnIndex(HouseEntry.COLUMN_HOUSE_AREA));
                    String houseBedRooms = getString(R.string.number_of_bed_rooms) + " ";
                    houseBedRooms += data.getString(data.getColumnIndex(HouseEntry.COLUMN_HOUSE_BED_ROOMS));
                    String houseBathRooms = getString(R.string.num_of_bath_rooms) + " ";
                    houseBathRooms += data.getString(data.getColumnIndex(HouseEntry.COLUMN_HOUSE_BATH_ROOMS));
                    String houseFloors = getString(R.string.num_of_floors_floor) + " ";
                    houseFloors += data.getString(data.getColumnIndex(HouseEntry.COLUMN_HOUSE_FLOORS));
                    String houseYearBuilt = getString(R.string._year_built) + " ";
                    houseYearBuilt += data.getString(data.getColumnIndex(HouseEntry.COLUMN_HOUSE_YEAR_BUILT));
                    String houseYardSize = getString(R.string._yard_size) + " ";
                    houseYardSize += data.getString(data.getColumnIndex(HouseEntry.COLUMN_HOUSE_YARD_SIZE)) + " m2";
                    String housePlace = getString(R.string._place) + " ";
                    housePlace += HouseEntry.getPlaceName(data.getInt(data.getColumnIndex(HouseEntry.COLUMN_HOUSE_PLACE)));
                    String house_content = data.getString(data.getColumnIndex(HouseEntry.COLUMN_HOUSE_CONTENT));
                    int houseState = data.getInt(data.getColumnIndex(HouseEntry.COLUMN_HOUSE_STATE));
                    int countViews = data.getInt(data.getColumnIndex(HouseEntry.COLUMN_HOUSE_COUNT_VIEWS));

                    //Query house type name
                    Integer houseType = data.getInt(data.getColumnIndex(HouseEntry.COLUMN_HOUSE_TYPE_ID));
                    String houseTypeName;
                    String[] projection = {HouseTypeEntry._ID, HouseTypeEntry.COLUMN_HOUSE_TYPE_NAME};
                    Cursor houseTypeCursor = mContext.getContentResolver().query(ContentUris.withAppendedId(HouseTypeEntry.CONTENT_URI, houseType.longValue()),
                            projection,
                            null,
                            null,
                            null);
                    // Proceed with moving to the first row of the cursor and reading data from it
                    // (This should be the only row in the cursor)
                    if (houseTypeCursor.moveToFirst()) {
                        houseTypeName = houseTypeCursor.getString(houseTypeCursor.getColumnIndex(HouseTypeEntry.COLUMN_HOUSE_TYPE_NAME));
                    } else {
                        houseTypeName = "";
                    }
                    //close the cursor
                    houseTypeCursor.close();

                    //format Price
                    if (!houseRentCost.equals(getString(R.string.sale_only))) {
                        if (houseType == 5 || houseType == 8) {
                            houseRentCost += "/Đêm";
                        } else {
                            houseRentCost += "/Tháng";
                        }
                    }
                    if (houseSalePrice.equals(mContext.getString(R.string.sale_only))) {
                        houseSalePrice = getString(R.string.rent_only);
                    }



                    //Update correspond text view
                    houseNameTV.setText(houseName);
                    houseAddressTV.setText(houseAddress);
                    rentCostTV.setText(houseRentCost);
                    salePriceTV.setText(houseSalePrice);
                    houseTypeTV.setText(houseTypeName);
                    houseAreaTV.setText(getString(R.string.area) + " " + houseArea + " m2");
                    countViewsTV.setText(countViews + " views");

                    String moreInfo = houseBedRooms + houseBathRooms + houseFloors +
                            houseYearBuilt + houseYardSize + housePlace;
                    moreInfo += "\n\n" + house_content;
                    moreInfoTV.setText(moreInfo);


                    //Calculate and display distance between the pointer location and the house
                    float distance = ShelterDBHelper.getDistanceFromHouseToThePointer(sessionManager, data);
                    if (distance != -1) {
                        String formatDistanceString = "From";
                        if (sessionManager.haveWishPointData()) {
                            formatDistanceString += " " + sessionManager.getWishfulPointName();
                        } else {
                            formatDistanceString += " you";
                        }
                        formatDistanceString += ": " + distance + " km";
                        nearPointDistanceTV.setText(formatDistanceString);
                    } else {
                        //Request permission to access user location
                        if (ActivityCompat.checkSelfPermission(
                                mActivity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                                mActivity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(mActivity, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MainActivity.REQUEST_LOCATION);
                        } else {
                            Toast.makeText(mContext, getString(R.string.unabale_to_locate_user_location), Toast.LENGTH_LONG).show();
                        }
                        nearPointDistanceTV.setText(R.string.unlocatable);
                    }
                    //Get houseLatLng for the deliver to locate house on google map fragment
                    houseLatLng = new LatLng(data.getDouble(data.getColumnIndex(HouseEntry.COLUMN_HOUSE_LATITUDE)),
                            data.getDouble(data.getColumnIndex(HouseEntry.COLUMN_HOUSE_LONGITUDE)));

                    //Init image adapter
                    imageSliderAdapter = new ImageSliderAdapter(mContext);

                    //Set adapter to slider view
                    sliderView.setSliderAdapter(imageSliderAdapter);


                    //Load list house's images
                    imageRequester.loadListRefToSliderAdapter(data.getInt(data.getColumnIndex(HouseEntry._ID)),
                            HouseEntry.TABLE_NAME,
                            imageSliderAdapter,
                            sliderView);
                    //Set image house close icon
                    if (houseState == HouseEntry.STATE_ABANDONED || houseState == HouseEntry.STATE_TRUE_DEATH) {
                        closeHouseIcon.setVisibility(View.VISIBLE);
                        Toast.makeText(mContext, R.string.close_house_alert, Toast.LENGTH_LONG).show();
                    } else {
                        closeHouseIcon.setVisibility(View.GONE);
                    }

                    break;
                case IS_FAVOURITE_LOADER:
                    isFavourite = true;
                    isFavouriteButton.setImageResource(R.drawable.outline_favorite_24);
                    cursorIsFavourite = data;
                    break;
                case CHECK_CONTACT_SENT:
                    checkContact = data.getInt(data.getColumnIndex(RatingEntry.COLUMN_STARS));
                    if (checkContact == RatingEntry.SEND_CONTACT || checkContact == RatingEntry.CONTACT_SOLVED) {
                        sendContactButton.setText(R.string.contact_sent);

                    }
                    sendContactButton.setBackgroundColor(getResources().getColor(R.color.colorAccent, null));
                    break;
            }
        }


    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {

    }

    @Override
    public void onDestroyView() {
        Long userID = ContentUris.parseId(sessionManager.getUserUri());
        Long houseID = ContentUris.parseId(mHouseUri);
        if (isFavourite && cursorIsFavourite == null) {
            ContentValues values = new ContentValues();
            values.put(RatingEntry.COLUMN_USER_ID, userID);
            values.put(RatingEntry.COLUMN_HOUSE_ID, houseID);
            values.put(RatingEntry.COLUMN_STARS, RatingEntry.FAVOURITE);
            mContext.getContentResolver().insert(RatingEntry.CONTENT_URI, values);
        } else if (!isFavourite && cursorIsFavourite != null) {
            String selection = "user_id = ? AND stars = ? AND house_id = ?";
            String[] selectionArgs = {userID.toString(), RatingEntry.FAVOURITE.toString(), houseID.toString()};
            mContext.getContentResolver().delete(RatingEntry.CONTENT_URI, selection, selectionArgs);
        }
        super.onDestroyView();
    }


}
