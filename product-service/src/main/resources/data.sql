INSERT INTO
    products (
        id,
        name,
        description,
        category,
        price,
        currency,
        stock,
        image_url,
        updated_at
    )
VALUES (
        '7e2a9b6d-1b15-4d09-a9a3-9ef7f9a8a001',
        'Настольная лампа Aurora',
        'Теплый свет и регулируемая стойка.',
        'Lighting',
        79.00,
        'USD',
        32,
        'https://images.unsplash.com/photo-1505691938895-1758d7feb511',
        now()
    ),
    (
        '7e2a9b6d-1b15-4d09-a9a3-9ef7f9a8a002',
        'Кресло Nimbus',
        'Дышащая сетка и каркас из ореха.',
        'Furniture',
        249.00,
        'USD',
        18,
        'https://images.unsplash.com/photo-1493666438817-866a91353ca9',
        now()
    ),
    (
        '7e2a9b6d-1b15-4d09-a9a3-9ef7f9a8a003',
        'Беспроводная колонка Echo',
        '360-градусный звук с мягким басом.',
        'Audio',
        129.00,
        'USD',
        40,
        'https://images.unsplash.com/photo-1503602642458-232111445657',
        now()
    ),
    (
        '7e2a9b6d-1b15-4d09-a9a3-9ef7f9a8a004',
        'Рюкзак Atlas',
        'Водоотталкивающий, с отделением для ноутбука.',
        'Accessories',
        99.00,
        'USD',
        55,
        'https://images.unsplash.com/photo-1500530855697-b586d89ba3ee',
        now()
    ),
    (
        '7e2a9b6d-1b15-4d09-a9a3-9ef7f9a8a005',
        'Чайный набор Solace',
        'Ручная керамика на двоих.',
        'Kitchen',
        64.00,
        'USD',
        27,
        'https://images.unsplash.com/photo-1509042239860-f550ce710b93',
        now()
    ),
    (
        '7e2a9b6d-1b15-4d09-a9a3-9ef7f9a8a006',
        'Наручные часы Drift',
        'Матовая сталь и льняной ремешок.',
        'Accessories',
        179.00,
        'USD',
        22,
        'https://images.unsplash.com/photo-1516574187841-cb9cc2ca948b',
        now()
    ),
    (
        '7e2a9b6d-1b15-4d09-a9a3-9ef7f9a8a007',
        'Плед Harbor',
        'Органический хлопок песочного оттенка.',
        'Home',
        59.00,
        'USD',
        45,
        'https://images.unsplash.com/photo-1503602642458-232111445657',
        now()
    ),
    (
        '7e2a9b6d-1b15-4d09-a9a3-9ef7f9a8a008',
        'Кашпо Nova',
        'Минималистичная керамика с дренажом.',
        'Home',
        34.00,
        'USD',
        60,
        'https://images.unsplash.com/photo-1493666438817-866a91353ca9',
        now()
    ) ON CONFLICT (id) DO NOTHING;
