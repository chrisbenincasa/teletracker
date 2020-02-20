import React from 'react';
import Link, { LinkProps } from 'next/link';

const RouterLink: React.ComponentType<LinkProps> = React.forwardRef(
  (props: LinkProps, ref?: React.Ref<HTMLAnchorElement>) => <Link {...props} />,
);

export default RouterLink;
