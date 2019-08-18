import React from 'react';
import { getPosterPath } from '../utils/metadata-access';
import { Thing } from '../types';
import imagePlaceholder from '../assets/images/imagePlaceholder.png';

interface imgProps {
  item: Thing;
}

export const ResponsiveImage: React.FC<imgProps> = ({ item }) => {
  let poster = getPosterPath(item);
  const baseImageURL = 'https://image.tmdb.org/t/p/';
  /* TODO: Figure out image/webp story and add here */
  const imageSpecs = [
    {
      type: 'image/jpeg',
      sizes: [92, 154, 342, 500, 780],
    },
  ];

  // This is a workaround because loading prop is not currently typed
  const imgProps = {
    loading: 'lazy',
  };

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
      return `${baseImageURL}w${size}${poster} ${size}w`;
    });

    return sourceSet.join(',');
  }

  return (
    <picture>
      {generateSource(imageSpecs)}
      <img
        data-async-image="true"
        src={imagePlaceholder}
        alt=""
        decoding="async"
        {...imgProps}
        style={{ width: '100%', objectFit: 'cover', height: '100%' }}
      />
    </picture>
  );
};
