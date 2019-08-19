import React from 'react';
import { Icon } from '@material-ui/core';
import { getMetadataPath } from '../utils/metadata-access';
import { Thing } from '../types';
import imagePlaceholder from '../assets/images/imagePlaceholder.png';

interface imgProps {
  item: Thing;
  imageType: 'poster' | 'backdrop';
  imageStyle?: object;
  pictureStyle?: object;
}

export const ResponsiveImage: React.FC<imgProps> = ({
  item,
  imageType,
  imageStyle,
  pictureStyle,
}) => {
  function generateSource(imageSpecs) {
    for (let x = 0; x <= imageSpecs.length; x++) {
      return (
        <source
          srcSet={generateSrcSet(imageSpecs[x].sizes)}
          type={imageSpecs[x].type}
        />
      );
    }
  }

  function generateSrcSet(supportedSizes: number[]) {
    const sourceSet = supportedSizes.map(size => {
      return `${baseImageURL}w${size}${imageName} ${size}w`;
    });

    return sourceSet.join(',');
  }

  let imageName = getMetadataPath(item, `${imageType}_path`);
  const baseImageURL = 'https://image.tmdb.org/t/p/';
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
      sizes: [300, 780, 1280],
    },
  ];

  const imageSpecs = imageType === 'poster' ? posterSpecs : backdropSpecs;

  // This is a workaround because loading prop is not currently typed
  const imgProps = {
    loading: 'lazy',
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
