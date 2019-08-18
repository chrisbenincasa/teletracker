import React from 'react';
import { getPosterPath } from '../utils/metadata-access';
import { Thing } from '../types';

interface imgProps {
  item: Thing;
}

export const ResponsiveImage: React.FC<imgProps> = ({ item }) => {
  let poster = getPosterPath(item);
  // This is a workaround because loading prop is not currently typed
  const imgProps = {
    loading: 'lazy',
  };

  return (
    <picture>
      {/* TODO: <source srcset="" type="image/webp"> */}
      {/* TODO: Make image util function to generate srcSet */}
      <source
        srcSet={
          'https://image.tmdb.org/t/p/w92' +
          poster +
          ' 92w, https://image.tmdb.org/t/p/w154' +
          poster +
          ' 154w, https://image.tmdb.org/t/p/w342' +
          poster +
          ' 342w, https://image.tmdb.org/t/p/w500' +
          poster +
          ' 500w, https://image.tmdb.org/t/p/w780' +
          poster +
          ' 780w'
        }
        type="image/jpeg"
      />
      <img
        data-async-image="true"
        src={'https://image.tmdb.org/t/p/w342' + poster}
        alt=""
        decoding="async"
        {...imgProps}
        style={{ width: '100%', objectFit: 'cover', height: '100%' }}
      />
    </picture>
  );
};
