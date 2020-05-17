import React, { useCallback } from 'react';
import { Icon, makeStyles, Theme } from '@material-ui/core';
import _ from 'lodash';
import { Item } from '../types/v2/Item';
import { Person } from '../types/v2/Person';
import { BASE_IMAGE_URL } from '../constants/';
import { ImageType } from '../types/';
import dequal from 'dequal';

// Stupid temporary solution.
const brokenImageCache = new Set();

const useStyles = makeStyles((theme: Theme) => ({
  fallbackImageWrapper: {
    display: 'flex',
    width: '100%',
    height: '100%',
    color: theme.palette.grey[500],
    backgroundColor: theme.palette.grey[300],
    fontSize: '10rem',
  },
  fallbackImageIcon: {
    alignSelf: 'center',
    margin: '0 auto',
    display: 'inline-block',
  },
}));

interface Props {
  readonly item: Item | Person;
  readonly imageType: ImageType;
  readonly imageStyle?: object;
  readonly loadCallback?: () => void;
}

// TODO: Refactor this entire thing to support more than just backdrop and poster
const ResponsiveImage = ({
  item,
  imageType,
  imageStyle,
  loadCallback,
}: Props) => {
  const classes = useStyles();

  function generateSource(imageSpecs) {
    return imageSpecs.map(image => {
      return (
        <source
          srcSet={generateSrcSet(image.sizes)}
          type={image.type}
          key={image.type}
        />
      );
    });
  }

  function generateSrcSet(supportedSizes: (number | string)[]) {
    const sourceSet = supportedSizes.map(size => {
      let sizeStr = size === 'original' ? '1600w' : `${size}w`;
      let urlPart = size === 'original' ? size : `w${size}`;
      return `${BASE_IMAGE_URL}${urlPart}${imageName} ${sizeStr}`;
    });

    return sourceSet.join(',');
  }

  let imageName;
  let img;
  switch (imageType) {
    case 'backdrop':
      img = _.find(
        item.images || [],
        image => image.provider_id === 0 && image.image_type === 'backdrop',
      );

      if (img) {
        imageName = img.id;
      }
      break;
    case 'poster':
      img = _.find(
        item.images || [],
        image => image.provider_id === 0 && image.image_type === 'poster',
      );

      if (img) {
        imageName = img.id;
      }
      break;
    case 'profile':
      img = _.find(
        item.images || [],
        image => image.provider_id === 0 && image.image_type === 'profile',
      );

      if (img) {
        imageName = img.id;
      }
      break;
  }

  /* TODO: Figure out image/webp story and add here */
  const posterSpecs = [
    {
      type: 'image/jpeg',
      sizes: [92, 154, 342, 500, 780],
    },
  ];

  const backdropSpecs = [
    {
      type: 'image/jpeg',
      sizes: [300, 780, 1280, 'original'],
    },
  ];

  const profileSpecs = [
    {
      type: 'image/jpeg',
      sizes: [45, 185, 632, 'original'],
    },
  ];

  let imageSpecs;
  if (imageType === 'profile') {
    imageSpecs = profileSpecs;
  } else if (imageType === 'backdrop') {
    imageSpecs = backdropSpecs;
  } else {
    imageSpecs = posterSpecs;
  }

  const handleOnLoad = useCallback(() => {
    if (loadCallback) {
      loadCallback();
    }
  }, []);

  const handleOnError = () => {
    brokenImageCache.add(imageName);
    if (loadCallback) {
      console.log('error load');
      loadCallback();
    }
  };

  const renderPlaceholder = () => {
    // Allow placeholder to fade in
    handleOnLoad();

    return (
      <div className={classes.fallbackImageWrapper} style={imageStyle}>
        <Icon className={classes.fallbackImageIcon} fontSize="inherit">
          {imageType === 'profile' ? 'person' : 'broken_image'}
        </Icon>
      </div>
    );
  };

  if (imageName) {
    // TODO: Figure out if we want to do a placeholder src. We took it away
    // because Firefox would flicker like crazy.
    return brokenImageCache.has(imageName) ? (
      renderPlaceholder()
    ) : (
      <img
        data-async-image="true"
        alt=""
        srcSet={generateSrcSet(imageSpecs[0].sizes)}
        decoding="async"
        style={imageStyle}
        itemProp="image"
        onLoad={handleOnLoad}
        onError={handleOnError}
      />
    );
  } else if (
    !imageName &&
    (imageType === 'poster' || imageType === 'profile')
  ) {
    // Override intersection observer to ensure content with no onLoad event displays
    handleOnLoad();
    return renderPlaceholder();
  } else {
    return null;
  }
};

export default React.memo(ResponsiveImage, dequal);
