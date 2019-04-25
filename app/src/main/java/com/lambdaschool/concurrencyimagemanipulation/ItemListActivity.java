package com.lambdaschool.concurrencyimagemanipulation;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.lambdaschool.concurrencyimagemanipulation.dummy.DummyContent;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

/**
 * An activity representing a list of Items. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link ItemDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
public class ItemListActivity extends AppCompatActivity {

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_item_list);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle(getTitle());

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        if (findViewById(R.id.item_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;
        }

        View recyclerView = findViewById(R.id.item_list);
        assert recyclerView != null;
        setupRecyclerView((RecyclerView) recyclerView);
    }

    private void setupRecyclerView(@NonNull RecyclerView recyclerView) {
        recyclerView.setAdapter(new SimpleItemRecyclerViewAdapter(this, mTwoPane));
    }

    public static class SimpleItemRecyclerViewAdapter
            extends RecyclerView.Adapter<SimpleItemRecyclerViewAdapter.ViewHolder> {

        private final ItemListActivity          mParentActivity;
        private final ArrayList<ImageContainer> mValues;
        private final boolean                   mTwoPane;

        private final Thread downloadThread, processingThread;
        private final Semaphore imageListLock;

        private final View.OnClickListener mOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DummyContent.DummyItem item = (DummyContent.DummyItem) view.getTag();
                if (mTwoPane) {
                    Bundle arguments = new Bundle();
                    arguments.putString(ItemDetailFragment.ARG_ITEM_ID, item.id);
                    ItemDetailFragment fragment = new ItemDetailFragment();
                    fragment.setArguments(arguments);
                    mParentActivity.getSupportFragmentManager().beginTransaction()
                                   .replace(R.id.item_detail_container, fragment)
                                   .commit();
                } else {
                    Context context = view.getContext();
                    Intent  intent  = new Intent(context, ItemDetailActivity.class);
                    intent.putExtra(ItemDetailFragment.ARG_ITEM_ID, item.id);

                    context.startActivity(intent);
                }
            }
        };

        SimpleItemRecyclerViewAdapter(ItemListActivity parent,
                                      boolean twoPane) {
            mValues = new ArrayList<ImageContainer>();

            // initialize list of urls
            mValues.add(new ImageContainer("https://cdn.spacetelescope.org/archives/images/publicationjpg/heic1215b.jpg"));
            mValues.add(new ImageContainer("https://i.redd.it/oal0dnbot2m21.jpg"));
            mValues.add(new ImageContainer("https://cdn.spacetelescope.org/archives/images/screen/heic1206a.jpg"));
            mValues.add(new ImageContainer("https://cdn.spacetelescope.org/archives/images/screen/heic0601a.jpg"));

            mParentActivity = parent;
            mTwoPane = twoPane;

            imageListLock = new Semaphore(1);

            downloadThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    if (mValues.size() > 0) {
                        for (final ImageContainer container : mValues) {
                            try {
                                imageListLock.acquire();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            NetworkAdapter.backgroundBitmapFromUrl(container.getUrl(), new NetworkAdapter.NetworkImageCallback() {
                                @Override
                                public void processImage(Bitmap image) {
                                    try {
                                        imageListLock.acquire();
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }

                                    container.setOriginal(image);

                                    imageListLock.release();
                                }
                            });
                            imageListLock.release();
                        }
                    }
                }
            });
            downloadThread.start();

            processingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < mValues.size(); ++i) {
                        try {
                            imageListLock.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        ImageContainer imageContainer = mValues.get(i);
                        imageListLock.release();

                        while (imageContainer.getOriginal() == null) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            try {
                                imageListLock.acquire();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            imageContainer = mValues.get(i);
                            imageListLock.release();
                        }

                        final Bitmap original = imageContainer.getOriginal();
                        final Bitmap resultBitmap = Bitmap.createBitmap(
                                original.getWidth(),
                                original.getHeight(),
                                Bitmap.Config.ARGB_8888);

                        for(int x = 0; x < resultBitmap.getWidth(); ++x) {
                            for(int y = 0; y < resultBitmap.getHeight(); ++y) {
                                int oldPixel = original.getPixel(x, y);

                                int greyValue = (Color.red(oldPixel) + Color.green(oldPixel) + Color.blue(oldPixel)) / 3; // 0xab
                                // 0xffababab
                                int newPixel = (((0xff * 0x100 + greyValue) * 0x100 + greyValue) * 0x100 + greyValue);
                                resultBitmap.setPixel(x, y, newPixel);
                            }
                        }
                        try {
                            imageListLock.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        mValues.get(i).setModified(resultBitmap);
                        imageListLock.release();
                    }
                }
            });
            processingThread.start();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                                      .inflate(R.layout.item_list_content, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, final int position) {

            new Thread(new Runnable() {
                @Override
                public void run() {

                    try {
                        imageListLock.acquire();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    ImageContainer imageContainer = mValues.get(position);
                    imageListLock.release();

                    while (imageContainer.getOriginal() == null) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        try {
                            imageListLock.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        imageContainer = mValues.get(position);
                        imageListLock.release();
                    }

                    final Bitmap original = imageContainer.getOriginal();
                    mParentActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            holder.mOriginalView.setImageBitmap(original);
                        }
                    });


                    try {
                        imageListLock.acquire();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    imageContainer = mValues.get(position);
                    imageListLock.release();

                    while (imageContainer.getModified() == null) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        try {
                            imageListLock.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        imageContainer = mValues.get(position);
                        imageListLock.release();
                    }

                    final Bitmap modified = imageContainer.getModified();
                    mParentActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            holder.mProcessedView.setImageBitmap(modified);
                        }
                    });
                }
            }).start();

            holder.itemView.setTag(mValues.get(position));
            holder.itemView.setOnClickListener(mOnClickListener);
        }

        @Override
        public int getItemCount() {
            return mValues.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            final ImageView mOriginalView;
            final ImageView mProcessedView;

            ViewHolder(View view) {
                super(view);
                mOriginalView = (ImageView) view.findViewById(R.id.id_text);
                mProcessedView = (ImageView) view.findViewById(R.id.content);
            }
        }
    }
}
