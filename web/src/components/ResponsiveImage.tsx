import React from 'react';
import { Icon } from '@material-ui/core';
import imagePlaceholder from '../assets/images/imagePlaceholder.png';
import _ from 'lodash';
import { Item } from '../types/v2/Item';
import { Person } from '../types/v2/Person';
import { BASE_IMAGE_URL } from '../constants/';
import { ImageType } from '../types/';

interface imgProps {
  // item: HasImagery;
  item: Item | Person;
  imageType: ImageType;
  imageStyle?: object;
  pictureStyle?: object;
  loadCallback?: () => void;
}

// TODO: Refactor this entire thing to support more than just backdrop and poster
export const ResponsiveImage: React.FC<imgProps> = ({
  item,
  imageType,
  imageStyle,
  pictureStyle,
  loadCallback,
}) => {
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

  // This is a workaround because loading prop is not currently typed
  const imgProps = {
    loading: 'lazy',
  };

  const handleOnLoad = () => {
    if (loadCallback) {
      loadCallback();
    }
  };

  if (imageName) {
    return (
      <picture style={pictureStyle}>
        {generateSource(imageSpecs)}
        <img
          data-async-image="true"
          src={imagePlaceholder}
          alt=""
          decoding="async"
          {...imgProps}
          style={imageStyle}
          itemProp="image"
          onLoad={handleOnLoad}
        />
      </picture>
    );
  } else if (!imageName && imageType === 'poster') {
    return (
      <div
        style={{
          display: 'flex',
          width: '100%',
          height: '100%',
          color: '#9e9e9e',
          backgroundColor: '#e0e0e0',
          fontSize: '10em',
        }}
      >
        <Icon
          style={{
            alignSelf: 'center',
            margin: '0 auto',
            display: 'inline-block',
          }}
          fontSize="inherit"
        >
          broken_image
        </Icon>
      </div>
    );
  } else {
    return null;
  }
};
